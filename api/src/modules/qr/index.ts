import { Hono } from "hono";
import { z } from "zod";
import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq, and } from "drizzle-orm";
import { generateId } from "../../lib/crypto.js";
import { requireAuth } from "../../middleware/auth.js";
import { requireTenant } from "../../middleware/tenant.js";
import { requirePermission } from "../../middleware/rbac.js";
import { NotFoundError } from "../../lib/errors.js";
import type { AppEnv } from "../../types/hono.js";

export const qrRouter = new Hono<AppEnv>();

qrRouter.use("*", requireAuth);

qrRouter.get("/:orgId/qr", requireTenant, async (c) => {
  const orgId = c.get("organizationId");

  const qrs = db
    .select({
      id: schema.qrCodes.id,
      title: schema.qrCodes.title,
      qrPayload: schema.qrCodes.qrPayload,
      type: schema.qrCodes.type,
      amount: schema.qrCodes.amount,
      note: schema.qrCodes.note,
      usageCount: schema.qrCodes.usageCount,
      vpa: schema.upiAccounts.vpa,
      payeeName: schema.upiAccounts.payeeName,
      createdAt: schema.qrCodes.createdAt,
    })
    .from(schema.qrCodes)
    .innerJoin(schema.upiAccounts, eq(schema.qrCodes.upiAccountId, schema.upiAccounts.id))
    .where(eq(schema.qrCodes.organizationId, orgId))
    .all();

  return c.json({ success: true, qrCodes: qrs });
});

qrRouter.post("/:orgId/qr", requireTenant, requirePermission("qr.create"), async (c) => {
  const orgId = c.get("organizationId");
  const actorId = c.get("userId");
  const body = await c.req.json();

  const validator = z.object({
    upiAccountId: z.string(),
    title: z.string().min(1),
    amount: z.number().positive().optional(),
    note: z.string().max(50).optional(),
    type: z.enum(["STATIC", "DYNAMIC"]).default("STATIC"),
  });

  const data = validator.parse(body);

  const upi = db
    .select()
    .from(schema.upiAccounts)
    .where(and(eq(schema.upiAccounts.id, data.upiAccountId), eq(schema.upiAccounts.organizationId, orgId)))
    .get();

  if (!upi) {
    throw new NotFoundError("UPI Account not found in this organization");
  }

  // Construct NPCI standard UPI URI
  const params = new URLSearchParams({
    pa: upi.vpa,
    pn: upi.payeeName,
    mc: upi.merchantCategoryCode,
    cu: "INR",
  });

  if (data.amount) {
    params.set("am", data.amount.toFixed(2));
  }
  if (data.note) {
    params.set("tn", data.note);
  }

  const qrPayload = `upi://pay?${params.toString()}`;
  const qrId = generateId("qr");
  const now = new Date();

  db.insert(schema.qrCodes)
    .values({
      id: qrId,
      organizationId: orgId,
      upiAccountId: upi.id,
      title: data.title,
      qrPayload,
      type: data.type,
      amount: data.amount ?? null,
      note: data.note ?? null,
      usageCount: 0,
      isActive: true,
      createdAt: now,
    })
    .run();

  db.insert(schema.auditLogs)
    .values({
      id: generateId("aud"),
      organizationId: orgId,
      actorId,
      action: "qr.created",
      resourceType: "qr_code",
      resourceId: qrId,
      metadataJson: JSON.stringify({ title: data.title, amount: data.amount }),
      createdAt: now,
    })
    .run();

  return c.json(
    {
      success: true,
      qrCode: {
        id: qrId,
        title: data.title,
        qrPayload,
        vpa: upi.vpa,
        payeeName: upi.payeeName,
        amount: data.amount,
        note: data.note,
        type: data.type,
      },
    },
    201
  );
});
