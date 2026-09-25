import { Hono } from "hono";
import { z } from "zod";
import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq, and, inArray } from "drizzle-orm";
import { generateId } from "../../lib/crypto.js";
import { requireAuth } from "../../middleware/auth.js";
import { requireTenant } from "../../middleware/tenant.js";
import { requirePermission } from "../../middleware/rbac.js";
import { NotFoundError } from "../../lib/errors.js";
import type { AppEnv } from "../../types/hono.js";

export const accountsRouter = new Hono<AppEnv>();

accountsRouter.use("*", requireAuth);

accountsRouter.get("/:orgId/accounts", requireTenant, requirePermission("accounts.read"), async (c) => {
  const orgId = c.get("organizationId");
  const accounts = await db
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
      isDefault: z.boolean().optional(),
    });

    const data = validator.parse(body);

    // Count existing accounts to determine default status
    const existing = await db
      .select({ id: schema.bankAccounts.id })
      .from(schema.bankAccounts)
      .where(eq(schema.bankAccounts.organizationId, orgId))
      .all();

    const shouldBeDefault = data.isDefault ?? existing.length === 0;

    if (shouldBeDefault) {
      await db.update(schema.bankAccounts)
        .set({ isDefault: false })
        .where(eq(schema.bankAccounts.organizationId, orgId))
        .run();
    }

    // Mask account number: keep only last 4 digits (Regulatory & Security boundary)
    const last4 = data.accountNumber.slice(-4);
    const accountNumberMasked = `••••••••${last4}`;
    const now = new Date();
    const accountId = generateId("bank");

    await db.insert(schema.bankAccounts)
      .values({
        id: accountId,
        organizationId: orgId,
        bankName: data.bankName,
        accountHolderName: data.accountHolderName,
        accountNumberMasked,
        ifscCode: data.ifscCode.toUpperCase(),
        accountType: data.accountType,
        isDefault: shouldBeDefault,
        status: "ACTIVE",
        createdAt: now,
        updatedAt: now,
      })
      .run();

    // Audit log
    await db.insert(schema.auditLogs)
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
          isDefault: shouldBeDefault,
        }),
        createdAt: now,
      })
      .run();

    return c.json(
      {
        success: true,
        account: {
          id: accountId,
          organizationId: orgId,
          bankName: data.bankName,
          accountHolderName: data.accountHolderName,
          accountNumberMasked,
          ifscCode: data.ifscCode.toUpperCase(),
          accountType: data.accountType,
          isDefault: shouldBeDefault,
          status: "ACTIVE",
        },
      },
      201
    );
  }
);

// Update bank account details
accountsRouter.patch(
  "/:orgId/accounts/:accountId",
  requireTenant,
  requirePermission("accounts.manage"),
  async (c) => {
    const orgId = c.get("organizationId");
    const accountId = c.req.param("accountId");
    const actorId = c.get("userId");
    const body = await c.req.json();

    const validator = z.object({
      bankName: z.string().min(2).optional(),
      accountHolderName: z.string().min(2).optional(),
      accountNumber: z.string().min(9).max(18).optional(),
      ifscCode: z.string().regex(/^[A-Z]{4}0[A-Z0-9]{6}$/, "Invalid IFSC code format").optional(),
      accountType: z.enum(["CURRENT", "SAVINGS", "OVERDRAFT"]).optional(),
      isDefault: z.boolean().optional(),
    });

    const data = validator.parse(body);

    const existing = await db
      .select()
      .from(schema.bankAccounts)
      .where(and(eq(schema.bankAccounts.id, accountId), eq(schema.bankAccounts.organizationId, orgId)))
      .get();

    if (!existing) {
      throw new NotFoundError("Bank account not found");
    }

    if (data.isDefault) {
      await db.update(schema.bankAccounts)
        .set({ isDefault: false })
        .where(eq(schema.bankAccounts.organizationId, orgId))
        .run();
    }

    const updateValues: Partial<typeof schema.bankAccounts.$inferInsert> = {
      updatedAt: new Date(),
    };

    if (data.bankName) updateValues.bankName = data.bankName;
    if (data.accountHolderName) updateValues.accountHolderName = data.accountHolderName;
    if (data.ifscCode) updateValues.ifscCode = data.ifscCode.toUpperCase();
    if (data.accountType) updateValues.accountType = data.accountType;
    if (data.isDefault !== undefined) updateValues.isDefault = data.isDefault;

    if (data.accountNumber) {
      const last4 = data.accountNumber.slice(-4);
      updateValues.accountNumberMasked = `••••••••${last4}`;
    }

    await db.update(schema.bankAccounts)
      .set(updateValues)
      .where(eq(schema.bankAccounts.id, accountId))
      .run();

    // Audit log
    await db.insert(schema.auditLogs)
      .values({
        id: generateId("aud"),
        organizationId: orgId,
        actorId,
        action: "account.updated",
        resourceType: "bank_account",
        resourceId: accountId,
        metadataJson: JSON.stringify(updateValues),
        createdAt: new Date(),
      })
      .run();

    const updated = await db
      .select()
      .from(schema.bankAccounts)
      .where(eq(schema.bankAccounts.id, accountId))
      .get();

    return c.json({
      success: true,
      message: "Bank account updated successfully",
      account: updated,
    });
  }
);

// Set bank account as default settlement account
accountsRouter.patch(
  "/:orgId/accounts/:accountId/default",
  requireTenant,
  requirePermission("accounts.manage"),
  async (c) => {
    const orgId = c.get("organizationId");
    const accountId = c.req.param("accountId");
    const actorId = c.get("userId");

    const account = await db
      .select()
      .from(schema.bankAccounts)
      .where(and(eq(schema.bankAccounts.id, accountId), eq(schema.bankAccounts.organizationId, orgId)))
      .get();

    if (!account) {
      throw new NotFoundError("Bank account not found");
    }

    // Unset all defaults for this organization
    await db.update(schema.bankAccounts)
      .set({ isDefault: false })
      .where(eq(schema.bankAccounts.organizationId, orgId))
      .run();

    // Set this account as default
    await db.update(schema.bankAccounts)
      .set({ isDefault: true, updatedAt: new Date() })
      .where(eq(schema.bankAccounts.id, accountId))
      .run();

    // Audit log
    await db.insert(schema.auditLogs)
      .values({
        id: generateId("aud"),
        organizationId: orgId,
        actorId,
        action: "account.set_default",
        resourceType: "bank_account",
        resourceId: accountId,
        metadataJson: JSON.stringify({ accountId }),
        createdAt: new Date(),
      })
      .run();

    return c.json({
      success: true,
      message: "Settlement account set as default successfully",
    });
  }
);

// Delete bank account
accountsRouter.delete(
  "/:orgId/accounts/:accountId",
  requireTenant,
  requirePermission("accounts.manage"),
  async (c) => {
    const orgId = c.get("organizationId");
    const accountId = c.req.param("accountId");
    const actorId = c.get("userId");

    const account = await db
      .select()
      .from(schema.bankAccounts)
      .where(and(eq(schema.bankAccounts.id, accountId), eq(schema.bankAccounts.organizationId, orgId)))
      .get();

    if (!account) {
      throw new NotFoundError("Bank account not found");
    }

    // Unlink UPI accounts attached to this bank account
    await db.update(schema.upiAccounts)
      .set({ bankAccountId: null })
      .where(eq(schema.upiAccounts.bankAccountId, accountId))
      .run();

    // Delete the bank account
    await db.delete(schema.bankAccounts)
      .where(eq(schema.bankAccounts.id, accountId))
      .run();

    // If deleted account was default, promote another account if available
    if (account.isDefault) {
      const nextAcc = await db
        .select()
        .from(schema.bankAccounts)
        .where(eq(schema.bankAccounts.organizationId, orgId))
        .limit(1)
        .get();

      if (nextAcc) {
        await db.update(schema.bankAccounts)
          .set({ isDefault: true })
          .where(eq(schema.bankAccounts.id, nextAcc.id))
          .run();
      }
    }

    // Audit log
    await db.insert(schema.auditLogs)
      .values({
        id: generateId("aud"),
        organizationId: orgId,
        actorId,
        action: "account.deleted",
        resourceType: "bank_account",
        resourceId: accountId,
        metadataJson: JSON.stringify({ accountId, bankName: account.bankName }),
        createdAt: new Date(),
      })
      .run();

    return c.json({
      success: true,
      message: "Bank account removed successfully",
    });
  }
);

accountsRouter.post(
  "/:orgId/accounts/bulk-delete",
  requireTenant,
  requirePermission("accounts.manage"),
  async (c) => {
    const orgId = c.get("organizationId");
    const actorId = c.get("userId");
    const body = await c.req.json();
    const validator = z.object({
      ids: z.array(z.string().min(1)).min(1),
    });
    const { ids } = validator.parse(body);

    const targets = await db
      .select()
      .from(schema.bankAccounts)
      .where(and(inArray(schema.bankAccounts.id, ids), eq(schema.bankAccounts.organizationId, orgId)))
      .all();

    if (targets.length === 0) {
      return c.json({ success: true, count: 0, message: "No matching bank accounts found to delete" });
    }

    const targetIds = targets.map((t) => t.id);

    // Unlink UPI accounts attached to these bank accounts
    await db.update(schema.upiAccounts)
      .set({ bankAccountId: null })
      .where(inArray(schema.upiAccounts.bankAccountId, targetIds))
      .run();

    // Delete the bank accounts
    await db.delete(schema.bankAccounts)
      .where(inArray(schema.bankAccounts.id, targetIds))
      .run();

    // If any deleted account was default, promote another account if available
    const hadDefault = targets.some((t) => t.isDefault);
    if (hadDefault) {
      const nextAcc = await db
        .select()
        .from(schema.bankAccounts)
        .where(eq(schema.bankAccounts.organizationId, orgId))
        .limit(1)
        .get();

      if (nextAcc) {
        await db.update(schema.bankAccounts)
          .set({ isDefault: true })
          .where(eq(schema.bankAccounts.id, nextAcc.id))
          .run();
      }
    }

    for (const target of targets) {
      await db.insert(schema.auditLogs)
        .values({
          id: generateId("aud"),
          organizationId: orgId,
          actorId,
          action: "account.bulk_deleted",
          resourceType: "bank_account",
          resourceId: target.id,
          metadataJson: JSON.stringify({ accountId: target.id, bankName: target.bankName }),
          createdAt: new Date(),
        })
        .run();
    }

    return c.json({
      success: true,
      count: targetIds.length,
      message: `Successfully deleted ${targetIds.length} bank account(s)`,
    });
  }
);
