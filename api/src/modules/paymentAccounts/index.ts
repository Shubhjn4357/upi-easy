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

export const paymentAccountsRouter = new Hono<AppEnv>();

// Public/Authenticated catalog of supported payment apps for detection
export const paymentAppsRouter = new Hono<AppEnv>();
paymentAppsRouter.get("/supported", (c) => {
  return c.json({
    success: true,
    data: [
      {
        id: "phonepe",
        displayName: "PhonePe",
        packageName: "com.phonepe.app",
        supported: true,
        parserKey: "phonepe",
      },
      {
        id: "google_pay",
        displayName: "Google Pay",
        packageName: "com.google.android.apps.nbu.paisa.user",
        supported: true,
        parserKey: "google_pay",
      },
    ],
  });
});

paymentAccountsRouter.use("*", requireAuth);

// List payment accounts for the organization
paymentAccountsRouter.get(
  "/:orgId/payment-accounts",
  requireTenant,
  requirePermission("upi.read"),
  async (c) => {
    const orgId = c.get("organizationId");
    const accounts = await db
      .select()
      .from(schema.paymentAccounts)
      .where(eq(schema.paymentAccounts.organizationId, orgId))
      .all();

    return c.json({ success: true, data: accounts });
  }
);

// Create new payment account
paymentAccountsRouter.post(
  "/:orgId/payment-accounts",
  requireTenant,
  requirePermission("upi.manage"),
  async (c) => {
    const orgId = c.get("organizationId");
    const body = await c.req.json();

    const validator = z.object({
      label: z.string().min(1, "Label is required").max(100),
      upiId: z
        .string()
        .min(3)
        .regex(/^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}$/, "Invalid UPI ID format (e.g. user@bank)"),
      paymentAppId: z.enum(["phonepe", "google_pay"]),
      paymentAppPackage: z.enum([
        "com.phonepe.app",
        "com.google.android.apps.nbu.paisa.user",
      ]),
      detectionEnabled: z.boolean().default(true),
      notificationAccessRequired: z.boolean().default(true),
    });

    const data = validator.parse(body);
    const id = generateId("pa");
    const now = new Date();

    const newAccount = {
      id,
      organizationId: orgId,
      label: data.label,
      upiId: data.upiId.toLowerCase().trim(),
      paymentAppId: data.paymentAppId,
      paymentAppPackage: data.paymentAppPackage,
      status: "ACTIVE" as const,
      detectionEnabled: data.detectionEnabled,
      notificationAccessRequired: data.notificationAccessRequired,
      lastNotificationDetectedAt: null,
      createdAt: now,
      updatedAt: now,
    };

    await db.insert(schema.paymentAccounts).values(newAccount).run();

    return c.json({ success: true, data: newAccount }, 201);
  }
);

// Update payment account
paymentAccountsRouter.patch(
  "/:orgId/payment-accounts/:id",
  requireTenant,
  requirePermission("upi.manage"),
  async (c) => {
    const orgId = c.get("organizationId");
    const id = c.req.param("id");
    const body = await c.req.json();

    const validator = z.object({
      label: z.string().min(1).max(100).optional(),
      upiId: z.string().regex(/^[\w.-]+@[\w.-]+$/, "Invalid UPI VPA format").optional(),
      status: z.enum(["ACTIVE", "INACTIVE", "ARCHIVED"]).optional(),
      detectionEnabled: z.boolean().optional(),
      notificationAccessRequired: z.boolean().optional(),
    });

    const data = validator.parse(body);
    const existing = await db
      .select()
      .from(schema.paymentAccounts)
      .where(
        and(
          eq(schema.paymentAccounts.id, id),
          eq(schema.paymentAccounts.organizationId, orgId)
        )
      )
      .get();

    if (!existing) {
      throw new NotFoundError("Payment account not found");
    }

    const now = new Date();
    await db
      .update(schema.paymentAccounts)
      .set({
        ...(data.label !== undefined && { label: data.label }),
        ...(data.upiId !== undefined && { upiId: data.upiId.toLowerCase().trim() }),
        ...(data.status !== undefined && { status: data.status }),
        ...(data.detectionEnabled !== undefined && {
          detectionEnabled: data.detectionEnabled,
        }),
        ...(data.notificationAccessRequired !== undefined && {
          notificationAccessRequired: data.notificationAccessRequired,
        }),
        updatedAt: now,
      })
      .where(eq(schema.paymentAccounts.id, id))
      .run();

    const updated = await db
      .select()
      .from(schema.paymentAccounts)
      .where(eq(schema.paymentAccounts.id, id))
      .get();

    return c.json({ success: true, data: updated });
  }
);

// Delete / Archive payment account
paymentAccountsRouter.delete(
  "/:orgId/payment-accounts/:id",
  requireTenant,
  requirePermission("upi.manage"),
  async (c) => {
    const orgId = c.get("organizationId");
    const id = c.req.param("id");

    const existing = await db
      .select()
      .from(schema.paymentAccounts)
      .where(
        and(
          eq(schema.paymentAccounts.id, id),
          eq(schema.paymentAccounts.organizationId, orgId)
        )
      )
      .get();

    if (!existing) {
      throw new NotFoundError("Payment account not found");
    }

    await db
      .delete(schema.paymentAccounts)
      .where(eq(schema.paymentAccounts.id, id))
      .run();

    return c.json({ success: true, message: "Payment account deleted successfully" });
  }
);
