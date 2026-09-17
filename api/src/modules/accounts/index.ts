import { Hono } from "hono";
import { z } from "zod";
import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq, and } from "drizzle-orm";
import { generateId } from "../../lib/crypto.js";
import { requireAuth } from "../../middleware/auth.js";
import { requireTenant } from "../../middleware/tenant.js";
import { requirePermission } from "../../middleware/rbac.js";
import type { AppEnv } from "../../types/hono.js";

export const accountsRouter = new Hono<AppEnv>();

accountsRouter.use("*", requireAuth);

accountsRouter.get("/:orgId/accounts", requireTenant, requirePermission("accounts.read"), async (c) => {
  const orgId = c.get("organizationId");
  const accounts = db
    .select()
    .from(schema.bankAccounts)
    .where(eq(schema.bankAccounts.organizationId, orgId))
    .all();

  return c.json({ success: true, accounts });
});

accountsRouter.post(
  "/:orgId/accounts",
  requireTenant,
  requirePermission("accounts.manage"),
  async (c) => {
    const orgId = c.get("organizationId");
    const actorId = c.get("userId");
    const body = await c.req.json();

    const validator = z.object({
      bankName: z.string().min(2),
      accountHolderName: z.string().min(2),
      accountNumber: z.string().min(9).max(18),
      ifscCode: z.string().regex(/^[A-Z]{4}0[A-Z0-9]{6}$/, "Invalid IFSC code format"),
      accountType: z.enum(["CURRENT", "SAVINGS", "OVERDRAFT"]).default("CURRENT"),
    });

    const data = validator.parse(body);

    // Mask account number: keep only last 4 digits (Regulatory & Security boundary)
    const last4 = data.accountNumber.slice(-4);
    const accountNumberMasked = `••••••••${last4}`;
    const now = new Date();
    const accountId = generateId("bank");

    db.insert(schema.bankAccounts)
      .values({
        id: accountId,
        organizationId: orgId,
        bankName: data.bankName,
        accountHolderName: data.accountHolderName,
        accountNumberMasked,
        ifscCode: data.ifscCode.toUpperCase(),
        accountType: data.accountType,
        status: "ACTIVE",
        createdAt: now,
        updatedAt: now,
      })
      .run();

    // Audit log
    db.insert(schema.auditLogs)
      .values({
        id: generateId("aud"),
        organizationId: orgId,
        actorId,
        action: "account.added",
        resourceType: "bank_account",
        resourceId: accountId,
        metadataJson: JSON.stringify({
          bankName: data.bankName,
          accountNumberMasked,
        }),
        createdAt: now,
      })
      .run();

    return c.json(
      {
        success: true,
        account: {
          id: accountId,
          bankName: data.bankName,
          accountHolderName: data.accountHolderName,
          accountNumberMasked,
          ifscCode: data.ifscCode.toUpperCase(),
          accountType: data.accountType,
          status: "ACTIVE",
        },
      },
      201
    );
  }
);
