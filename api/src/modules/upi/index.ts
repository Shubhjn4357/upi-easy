import { Hono } from "hono";
import { z } from "zod";
import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq, and } from "drizzle-orm";
import { generateId } from "../../lib/crypto.js";
import { requireAuth } from "../../middleware/auth.js";
import { requireTenant } from "../../middleware/tenant.js";
import { requirePermission } from "../../middleware/rbac.js";
import { AppError, NotFoundError } from "../../lib/errors.js";
import type { AppEnv } from "../../types/hono.js";

export const upiRouter = new Hono<AppEnv>();

upiRouter.use("*", requireAuth);

upiRouter.get("/:orgId/upi", requireTenant, requirePermission("upi.read"), async (c) => {
  const orgId = c.get("organizationId");

  const upiList = db
    .select({
      id: schema.upiAccounts.id,
      vpa: schema.upiAccounts.vpa,
      payeeName: schema.upiAccounts.payeeName,
      merchantCategoryCode: schema.upiAccounts.merchantCategoryCode,
      isDefault: schema.upiAccounts.isDefault,
      status: schema.upiAccounts.status,
      transactionCount: schema.upiAccounts.transactionCount,
      bankAccountId: schema.upiAccounts.bankAccountId,
      bankName: schema.bankAccounts.bankName,
      accountNumberMasked: schema.bankAccounts.accountNumberMasked,
      createdAt: schema.upiAccounts.createdAt,
    })
    .from(schema.upiAccounts)
    .leftJoin(schema.bankAccounts, eq(schema.upiAccounts.bankAccountId, schema.bankAccounts.id))
    .where(eq(schema.upiAccounts.organizationId, orgId))
    .all();

  return c.json({ success: true, upiAccounts: upiList });
});

upiRouter.post("/:orgId/upi", requireTenant, requirePermission("upi.manage"), async (c) => {
  const orgId = c.get("organizationId");
  const actorId = c.get("userId");
  const body = await c.req.json();

  const validator = z.object({
    vpa: z.string().regex(/^[\w.-]+@[\w.-]+$/, "Invalid UPI VPA format (e.g. name@bank)"),
    payeeName: z.string().min(2),
    bankAccountId: z.string().optional(),
    merchantCategoryCode: z.string().default("5411"),
    isDefault: z.boolean().default(false),
  });

  const data = validator.parse(body);

  // If set to default, unset other defaults in this organization
  if (data.isDefault) {
    db.update(schema.upiAccounts)
      .set({ isDefault: false })
      .where(eq(schema.upiAccounts.organizationId, orgId))
      .run();
  }

  const now = new Date();
  const upiId = generateId("upi");

  db.insert(schema.upiAccounts)
    .values({
      id: upiId,
      organizationId: orgId,
      bankAccountId: data.bankAccountId ?? null,
      vpa: data.vpa.toLowerCase(),
      payeeName: data.payeeName,
      merchantCategoryCode: data.merchantCategoryCode,
      isDefault: data.isDefault,
      status: "ACTIVE",
      transactionCount: 0,
      createdAt: now,
      updatedAt: now,
    })
    .run();

  // Also generate a default static QR code for this UPI address per NPCI UPI spec
  const qrId = generateId("qr");
  const params = new URLSearchParams({
    pa: data.vpa.toLowerCase(),
    pn: data.payeeName,
    mc: data.merchantCategoryCode,
    cu: "INR",
  });
  const upiUri = `upi://pay?${params.toString()}`;

  db.insert(schema.qrCodes)
    .values({
      id: qrId,
      organizationId: orgId,
      upiAccountId: upiId,
      title: `${data.payeeName} Primary QR`,
      qrPayload: upiUri,
      type: "STATIC",
      usageCount: 0,
      isActive: true,
      createdAt: now,
    })
    .run();

  // Audit log
  db.insert(schema.auditLogs)
    .values({
      id: generateId("aud"),
      organizationId: orgId,
      actorId,
      action: "upi.added",
      resourceType: "upi_account",
      resourceId: upiId,
      metadataJson: JSON.stringify({ vpa: data.vpa }),
      createdAt: now,
    })
    .run();

  return c.json(
    {
      success: true,
      upiAccount: {
        id: upiId,
        vpa: data.vpa.toLowerCase(),
        payeeName: data.payeeName,
        isDefault: data.isDefault,
        defaultQrPayload: upiUri,
      },
    },
    201
  );
});

upiRouter.patch(
  "/:orgId/upi/:upiId/default",
  requireTenant,
  requirePermission("upi.manage"),
  async (c) => {
    const orgId = c.get("organizationId");
    const actorId = c.get("userId");
    const upiId = c.req.param("upiId");

    const target = db
      .select()
      .from(schema.upiAccounts)
      .where(and(eq(schema.upiAccounts.id, upiId), eq(schema.upiAccounts.organizationId, orgId)))
      .get();

    if (!target) {
      throw new NotFoundError("UPI Account not found");
    }

    // Unset current default
    db.update(schema.upiAccounts)
      .set({ isDefault: false })
      .where(eq(schema.upiAccounts.organizationId, orgId))
      .run();

    // Set new default
    db.update(schema.upiAccounts)
      .set({ isDefault: true, updatedAt: new Date() })
      .where(eq(schema.upiAccounts.id, upiId))
      .run();

    return c.json({ success: true, message: "Default UPI ID updated" });
  }
);
