import { Hono } from "hono";
import { z } from "zod";
import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq, and, sql, desc, gte } from "drizzle-orm";
import { generateId } from "../../lib/crypto.js";
import { requireAuth } from "../../middleware/auth.js";
import { requireTenant } from "../../middleware/tenant.js";
import { requirePermission } from "../../middleware/rbac.js";
import { ForbiddenError, NotFoundError } from "../../lib/errors.js";
import type { AppEnv } from "../../types/hono.js";

export const organizationsRouter = new Hono<AppEnv>();

organizationsRouter.use("*", requireAuth);

organizationsRouter.get("/", async (c) => {
  const userId = c.get("userId");

  const orgRows = await db
    .select({
      id: schema.organizations.id,
      name: schema.organizations.name,
      legalBusinessName: schema.organizations.legalBusinessName,
      category: schema.organizations.category,
      panNumber: schema.organizations.panNumber,
      gstin: schema.organizations.gstin,
      status: schema.organizations.status,
      roleId: schema.organizationMembers.roleId,
      role: schema.roles.name,
      createdAt: schema.organizations.createdAt,
    })
    .from(schema.organizations)
    .innerJoin(
      schema.organizationMembers,
      eq(schema.organizations.id, schema.organizationMembers.organizationId)
    )
    .leftJoin(schema.roles, eq(schema.organizationMembers.roleId, schema.roles.id))
    .where(
      and(
        eq(schema.organizationMembers.userId, userId),
        eq(schema.organizationMembers.status, "ACTIVE")
      )
    )
    .all();

  // Attach permissions for each organization
  const orgsWithPermissions = await Promise.all(
    orgRows.map(async (org: any) => {
      const roleName = org.role || "MEMBER";
      let permissions: string[] = [];
      if (roleName === "OWNER") {
        permissions = ["*"];
      } else if (org.roleId) {
        const permRows = await db
          .select({ name: schema.permissions.name })
          .from(schema.rolePermissions)
          .innerJoin(schema.permissions, eq(schema.rolePermissions.permissionId, schema.permissions.id))
          .where(eq(schema.rolePermissions.roleId, org.roleId))
          .all();
        permissions = permRows.map((p: { name: string }) => p.name);
      }
      return {
        id: org.id,
        name: org.name,
        legalBusinessName: org.legalBusinessName,
        category: org.category,
        panNumber: org.panNumber,
        gstin: org.gstin,
        status: org.status,
        role: roleName,
        permissions,
        createdAt: org.createdAt,
      };
    })
  );

  return c.json({ success: true, organizations: orgsWithPermissions });
});

organizationsRouter.post("/", async (c) => {
  const userId = c.get("userId");
  const body = await c.req.json();
  const validator = z.object({
    name: z.string().min(2, "Business name is required"),
    legalBusinessName: z.string().optional(),
    category: z.string().default("RETAIL"),
    panNumber: z.string().optional(),
    gstin: z.string().optional(),
  });

  const data = validator.parse(body);
  const now = new Date();
  const orgId = generateId("org");

  // Create organization
  await db.insert(schema.organizations)
    .values({
      id: orgId,
      name: data.name,
      legalBusinessName: data.legalBusinessName ?? null,
      category: data.category,
      panNumber: data.panNumber ?? null,
      gstin: data.gstin ?? null,
      status: "ACTIVE",
      ownerId: userId,
      createdAt: now,
      updatedAt: now,
    })
    .run();

  // Add user as OWNER
  await db.insert(schema.organizationMembers)
    .values({
      id: generateId("mem"),
      organizationId: orgId,
      userId,
      roleId: "role_owner",
      status: "ACTIVE",
      joinedAt: now,
      createdAt: now,
      updatedAt: now,
    })
    .run();

  // Initialize notification preferences
  await db.insert(schema.notificationPreferences)
    .values({
      id: generateId("notif_pref"),
      userId,
      organizationId: orgId,
      paymentAlerts: true,
      staffAlerts: true,
      securityAlerts: true,
      voiceAnnouncements: false,
      updatedAt: now,
    })
    .run();

  // Record audit log
  await db.insert(schema.auditLogs)
    .values({
      id: generateId("aud"),
      organizationId: orgId,
      actorId: userId,
      action: "organization.created",
      resourceType: "organization",
      resourceId: orgId,
      metadataJson: JSON.stringify({ name: data.name }),
      createdAt: now,
    })
    .run();

  // Outbox event
  await db.insert(schema.outboxEvents)
    .values({
      id: generateId("evt"),
      organizationId: orgId,
      eventType: "organization.created",
      payloadJson: JSON.stringify({ organizationId: orgId, name: data.name }),
      status: "PENDING",
      createdAt: now,
    })
    .run();

  return c.json(
    {
      success: true,
      organization: {
        id: orgId,
        name: data.name,
        legalBusinessName: data.legalBusinessName ?? null,
        category: data.category,
        panNumber: data.panNumber ?? null,
        gstin: data.gstin ?? null,
        status: "ACTIVE",
        role: "OWNER",
      },
    },
    201
  );
});

organizationsRouter.patch("/:orgId", requireTenant, requirePermission("organization.manage"), async (c) => {
  const orgId = c.get("organizationId");
  const actorId = c.get("userId");
  const body = await c.req.json();
  const validator = z.object({
    name: z.string().min(2, "Business name is required").optional(),
    legalBusinessName: z.string().optional(),
    category: z.string().optional(),
    panNumber: z.string().optional(),
    gstin: z.string().optional(),
  });
  const data = validator.parse(body);
  const now = new Date();

  await db.update(schema.organizations)
    .set({
      ...(data.name ? { name: data.name } : {}),
      ...(data.legalBusinessName !== undefined ? { legalBusinessName: data.legalBusinessName } : {}),
      ...(data.category ? { category: data.category } : {}),
      ...(data.panNumber !== undefined ? { panNumber: data.panNumber ? data.panNumber.toUpperCase() : null } : {}),
      ...(data.gstin !== undefined ? { gstin: data.gstin ? data.gstin.toUpperCase() : null } : {}),
      updatedAt: now,
    })
    .where(eq(schema.organizations.id, orgId))
    .run();

  await db.insert(schema.auditLogs)
    .values({
      id: generateId("aud"),
      organizationId: orgId,
      actorId,
      action: "organization.updated",
      resourceType: "organization",
      resourceId: orgId,
      metadataJson: JSON.stringify(data),
      createdAt: now,
    })
    .run();

  return c.json({ success: true, message: "Organization updated successfully" });
});

// Delete Organization (Strictly Restricted to OWNER)
organizationsRouter.delete("/:orgId", requireTenant, async (c) => {
  const role = c.get("role");
  if (role !== "OWNER") {
    throw new ForbiddenError("Only the business OWNER is permitted to delete this organization");
  }

  const orgId = c.get("organizationId");
  const actorId = c.get("userId");

  // Cascade delete dependent entities
  await db.delete(schema.organizationMembers).where(eq(schema.organizationMembers.organizationId, orgId)).run();
  await db.delete(schema.organizationInvites).where(eq(schema.organizationInvites.organizationId, orgId)).run();
  await db.delete(schema.upiAccounts).where(eq(schema.upiAccounts.organizationId, orgId)).run();
  await db.delete(schema.bankAccounts).where(eq(schema.bankAccounts.organizationId, orgId)).run();
  await db.delete(schema.qrCodes).where(eq(schema.qrCodes.organizationId, orgId)).run();
  await db.delete(schema.notificationPreferences).where(eq(schema.notificationPreferences.organizationId, orgId)).run();
  await db.delete(schema.organizations).where(eq(schema.organizations.id, orgId)).run();

  await db.insert(schema.auditLogs)
    .values({
      id: generateId("aud"),
      organizationId: orgId,
      actorId,
      action: "organization.deleted",
      resourceType: "organization",
      resourceId: orgId,
      metadataJson: JSON.stringify({ actorId, deletedAt: new Date().toISOString() }),
      createdAt: new Date(),
    })
    .run();

  return c.json({ success: true, message: "Organization and all associated data deleted successfully" });
});

// Full App Setup Form (Business Name, Mobile, Primary UPI, Bank Account)
organizationsRouter.post("/setup", async (c) => {
  const userId = c.get("userId");
  const body = await c.req.json();
  const validator = z.object({
    businessName: z.string().min(2, "Business name is required"),
    legalBusinessName: z.string().optional(),
    mobileNumber: z.string().regex(/^[6-9]\d{9}$/, "Invalid 10-digit mobile number"),
    primaryVpa: z.string().regex(/^[\w.-]+@[\w.-]+$/, "Invalid UPI VPA (e.g. name@bank)"),
    payeeName: z.string().min(2, "Payee name is required"),
    bankName: z.string().optional(),
    accountNumber: z.string().optional(),
    ifscCode: z.string().optional(),
    category: z.string().default("RETAIL"),
    panNumber: z.string().optional(),
    gstin: z.string().optional(),
  });

  const data = validator.parse(body);
  const now = new Date();
  const orgId = generateId("org");

  // 1. Update user's mobile number
  await db.update(schema.users)
    .set({
      mobileNumber: data.mobileNumber,
      updatedAt: now,
    })
    .where(eq(schema.users.id, userId))
    .run();

  // 2. Create organization
  await db.insert(schema.organizations)
    .values({
      id: orgId,
      name: data.businessName,
      legalBusinessName: data.legalBusinessName ?? null,
      category: data.category,
      panNumber: data.panNumber ? data.panNumber.toUpperCase() : null,
      gstin: data.gstin ? data.gstin.toUpperCase() : null,
      status: "ACTIVE",
      ownerId: userId,
      createdAt: now,
      updatedAt: now,
    })
    .run();

  // 3. Add user as OWNER
  await db.insert(schema.organizationMembers)
    .values({
      id: generateId("mem"),
      organizationId: orgId,
      userId,
      roleId: "role_owner",
      status: "ACTIVE",
      joinedAt: now,
      createdAt: now,
      updatedAt: now,
    })
    .run();

  // 4. Optional Bank Account
  let bankAccountId: string | null = null;
  if (data.bankName && data.accountNumber && data.ifscCode) {
    bankAccountId = generateId("bank");
    const last4 = data.accountNumber.slice(-4);
    await db.insert(schema.bankAccounts)
      .values({
        id: bankAccountId,
        organizationId: orgId,
        bankName: data.bankName,
        accountHolderName: data.payeeName,
        accountNumberMasked: `••••••••${last4}`,
        ifscCode: data.ifscCode.toUpperCase(),
        accountType: "CURRENT",
        status: "ACTIVE",
        createdAt: now,
        updatedAt: now,
      })
      .run();
  }

  // 5. Create default UPI ID
  const upiId = generateId("upi");
  const normalizedVpa = data.primaryVpa.toLowerCase();
  await db.insert(schema.upiAccounts)
    .values({
      id: upiId,
      organizationId: orgId,
      bankAccountId,
      vpa: normalizedVpa,
      payeeName: data.payeeName,
      merchantCategoryCode: "5411",
      isDefault: true,
      status: "ACTIVE",
      transactionCount: 0,
      createdAt: now,
      updatedAt: now,
    })
    .run();

  // 6. Generate default static QR
  const qrParams = new URLSearchParams({
    pa: normalizedVpa,
    pn: data.payeeName,
    mc: "5411",
    cu: "INR",
  });
  const qrPayload = `upi://pay?${qrParams.toString()}`;
  await db.insert(schema.qrCodes)
    .values({
      id: generateId("qr"),
      organizationId: orgId,
      upiAccountId: upiId,
      title: `${data.businessName} Primary QR`,
      qrPayload,
      type: "STATIC",
      usageCount: 0,
      isActive: true,
      createdAt: now,
    })
    .run();

  // 7. Initialize Notification Preferences
  await db.insert(schema.notificationPreferences)
    .values({
      id: generateId("notif_pref"),
      userId,
      organizationId: orgId,
      paymentAlerts: true,
      staffAlerts: true,
      securityAlerts: true,
      voiceAnnouncements: false,
      updatedAt: now,
    })
    .run();

  // 8. Audit log
  await db.insert(schema.auditLogs)
    .values({
      id: generateId("aud"),
      organizationId: orgId,
      actorId: userId,
      action: "organization.setup_completed",
      resourceType: "organization",
      resourceId: orgId,
      metadataJson: JSON.stringify({ businessName: data.businessName, vpa: normalizedVpa }),
      createdAt: now,
    })
    .run();

  // 9. Outbox events for delta-sync
  await db.insert(schema.outboxEvents)
    .values([
      {
        id: generateId("evt"),
        organizationId: orgId,
        eventType: "organization.created",
        payloadJson: JSON.stringify({ organizationId: orgId, name: data.businessName }),
        status: "PENDING",
        createdAt: now,
      },
      {
        id: generateId("evt"),
        organizationId: orgId,
        eventType: "upi.created",
        payloadJson: JSON.stringify({
          id: upiId,
          organizationId: orgId,
          bankAccountId,
          vpa: normalizedVpa,
          payeeName: data.payeeName,
          merchantCategoryCode: "5411",
          isDefault: true,
          status: "ACTIVE",
          transactionCount: 0,
        }),
        status: "PENDING",
        createdAt: now,
      }
    ])
    .run();

  return c.json(
    {
      success: true,
      message: "Setup completed successfully",
      organization: {
        id: orgId,
        name: data.businessName,
        legalBusinessName: data.legalBusinessName ?? null,
        category: data.category,
        panNumber: data.panNumber ? data.panNumber.toUpperCase() : null,
        gstin: data.gstin ? data.gstin.toUpperCase() : null,
        status: "ACTIVE",
        role: "OWNER",
      },
      upiAccount: {
        id: upiId,
        vpa: normalizedVpa,
        payeeName: data.payeeName,
        merchantCategoryCode: "5411",
        isDefault: true,
        status: "ACTIVE",
        transactionCount: 0,
        qrPayload,
      },
    },
    201
  );
});

organizationsRouter.get("/:orgId", requireTenant, async (c) => {
  const orgId = c.get("organizationId");
  const org = await db.select().from(schema.organizations).where(eq(schema.organizations.id, orgId)).get();

  if (!org) {
    throw new NotFoundError("Organization not found");
  }

  return c.json({ success: true, organization: org });
});

organizationsRouter.get("/:orgId/dashboard", requireTenant, async (c) => {
  const orgId = c.get("organizationId");

  // Start of today in UTC / local
  const startOfToday = new Date();
  startOfToday.setHours(0, 0, 0, 0);

  // Today's received transactions
  const receivedToday = await db
    .select({
      total: sql<number>`coalesce(sum(${schema.transactions.amount}), 0)`,
      count: sql<number>`count(*)`,
    })
    .from(schema.transactions)
    .where(
      and(
        eq(schema.transactions.organizationId, orgId),
        eq(schema.transactions.direction, "RECEIVED"),
        eq(schema.transactions.status, "SUCCESS"),
        gte(schema.transactions.occurredAt, startOfToday)
      )
    )
    .get();

  // Today's sent transactions
  const sentToday = await db
    .select({
      total: sql<number>`coalesce(sum(${schema.transactions.amount}), 0)`,
      count: sql<number>`count(*)`,
    })
    .from(schema.transactions)
    .where(
      and(
        eq(schema.transactions.organizationId, orgId),
        eq(schema.transactions.direction, "SENT"),
        eq(schema.transactions.status, "SUCCESS"),
        gte(schema.transactions.occurredAt, startOfToday)
      )
    )
    .get();

  // Pending count
  const pending = await db
    .select({ count: sql<number>`count(*)` })
    .from(schema.transactions)
    .where(
      and(
        eq(schema.transactions.organizationId, orgId),
        eq(schema.transactions.status, "PENDING")
      )
    )
    .get();

  // Failed count
  const failed = await db
    .select({ count: sql<number>`count(*)` })
    .from(schema.transactions)
    .where(
      and(
        eq(schema.transactions.organizationId, orgId),
        eq(schema.transactions.status, "FAILED")
      )
    )
    .get();

  // Active UPI IDs count
  const activeUpi = await db
    .select({ count: sql<number>`count(*)` })
    .from(schema.upiAccounts)
    .where(
      and(
        eq(schema.upiAccounts.organizationId, orgId),
        eq(schema.upiAccounts.status, "ACTIVE")
      )
    )
    .get();

  // Recent 5 transactions
  const recentTransactions = await db
    .select()
    .from(schema.transactions)
    .where(eq(schema.transactions.organizationId, orgId))
    .orderBy(desc(schema.transactions.occurredAt))
    .limit(5)
    .all();

  return c.json({
    success: true,
    dashboard: {
      todayReceived: {
        amount: receivedToday?.total ?? 0,
        count: receivedToday?.count ?? 0,
      },
      todaySent: {
        amount: sentToday?.total ?? 0,
        count: sentToday?.count ?? 0,
      },
      pendingCount: pending?.count ?? 0,
      failedCount: failed?.count ?? 0,
      activeUpiCount: activeUpi?.count ?? 0,
      recentTransactions,
    },
  });
});
