import { Hono } from "hono";
import { z } from "zod";
import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq, and, or } from "drizzle-orm";
import { generateId } from "../../lib/crypto.js";
import { requireAuth } from "../../middleware/auth.js";
import { requireTenant } from "../../middleware/tenant.js";
import { requirePermission } from "../../middleware/rbac.js";
import { AppError, NotFoundError } from "../../lib/errors.js";
import { normalizeIndianMobileNumber, get10DigitMobile } from "../../lib/phone.js";
import { sendFcmToDevice } from "../../lib/fcm.js";
import type { AppEnv } from "../../types/hono.js";

export const membersRouter = new Hono<AppEnv>();

membersRouter.use("*", requireAuth);

membersRouter.get("/:orgId/staff", requireTenant, requirePermission("staff.read"), async (c) => {
  const orgId = c.get("organizationId");

  const staff = await db
    .select({
      id: schema.organizationMembers.id,
      userId: schema.users.id,
      fullName: schema.users.fullName,
      mobileNumber: schema.users.mobileNumber,
      email: schema.users.email,
      role: schema.roles.name,
      roleDescription: schema.roles.description,
      status: schema.organizationMembers.status,
      joinedAt: schema.organizationMembers.joinedAt,
      createdAt: schema.organizationMembers.createdAt,
    })
    .from(schema.organizationMembers)
    .innerJoin(schema.users, eq(schema.organizationMembers.userId, schema.users.id))
    .leftJoin(schema.roles, eq(schema.organizationMembers.roleId, schema.roles.id))
    .where(eq(schema.organizationMembers.organizationId, orgId))
    .all();

  const formatted = staff.map((s: any) => ({
    ...s,
    role: s.role || "MEMBER",
  }));

  return c.json({ success: true, staff: formatted });
});

// List invitations sent by this organization
membersRouter.get("/:orgId/invites", requireTenant, requirePermission("staff.read"), async (c) => {
  const orgId = c.get("organizationId");

  try {
    const invites = await db
      .select({
        id: schema.organizationInvites.id,
        organizationId: schema.organizationInvites.organizationId,
        invitedUserId: schema.organizationInvites.invitedUserId,
        invitedMobile: schema.organizationInvites.invitedMobile,
        invitedEmail: schema.organizationInvites.invitedEmail,
        invitedName: schema.organizationInvites.invitedName,
        role: schema.organizationInvites.role,
        status: schema.organizationInvites.status,
        expiresAt: schema.organizationInvites.expiresAt,
        acceptedAt: schema.organizationInvites.acceptedAt,
        rejectedAt: schema.organizationInvites.rejectedAt,
        cancelledAt: schema.organizationInvites.cancelledAt,
        createdAt: schema.organizationInvites.createdAt,
        invitedBy: schema.organizationInvites.invitedBy,
      })
      .from(schema.organizationInvites)
      .where(eq(schema.organizationInvites.organizationId, orgId))
      .all();

    return c.json({ success: true, invites });
  } catch (_: any) {
    return c.json({ success: true, invites: [] });
  }
});

const createInviteHandler = async (c: any) => {
  const orgId = c.get("organizationId");
  const actorId = c.get("userId");
  const body = await c.req.json();

  const validator = z.object({
    mobileNumber: z.string().optional().nullable(),
    email: z.string().email().optional().nullable(),
    name: z.string().optional().nullable(),
    fullName: z.string().optional().nullable(),
    role: z.enum(["MANAGER", "CASHIER", "ACCOUNTANT"]),
  }).refine((data) => Boolean(data.mobileNumber || data.email), {
    message: "Either mobileNumber or email must be provided",
  });

  const parsed = validator.parse(body);
  const rawMobile = parsed.mobileNumber?.trim() || undefined;
  const email = parsed.email?.trim()?.toLowerCase() || undefined;
  const name = parsed.name?.trim() || parsed.fullName?.trim() || undefined;
  const role = parsed.role;

  // 1. Normalize mobile if present
  let normalizedMobile: string | undefined;
  let mobile10: string | undefined;
  if (rawMobile) {
    normalizedMobile = normalizeIndianMobileNumber(rawMobile);
    mobile10 = get10DigitMobile(rawMobile);
  }

  // 2. Fetch organization info
  const org = await db.select().from(schema.organizations).where(eq(schema.organizations.id, orgId)).get();
  if (!org) {
    throw new NotFoundError("Organization not found");
  }

  // 3. Find existing user if available
  const actor = await db.select().from(schema.users).where(eq(schema.users.id, actorId)).get();

  let existingUser: typeof schema.users.$inferSelect | undefined;
  if (normalizedMobile && mobile10) {
    existingUser = await db
      .select()
      .from(schema.users)
      .where(
        or(
          eq(schema.users.mobileNumber, normalizedMobile),
          eq(schema.users.mobileNumber, mobile10)
        )
      )
      .get();
  }
  if (!existingUser && email) {
    existingUser = await db
      .select()
      .from(schema.users)
      .where(eq(schema.users.email, email))
      .get();
  }

  // 4. Prevent inviting self
  const actorNormalizedMobile = actor?.mobileNumber ? normalizeIndianMobileNumber(actor.mobileNumber) : null;
  const isSelfInvite =
    (existingUser && existingUser.id === actorId) ||
    (normalizedMobile && actorNormalizedMobile && normalizedMobile === actorNormalizedMobile) ||
    (email && actor?.email && email.toLowerCase() === actor.email.toLowerCase());

  if (isSelfInvite) {
    throw new AppError("You cannot invite yourself to the organization", 400, "CANNOT_INVITE_SELF");
  }

  // 5. Prevent duplicate active membership
  if (existingUser) {
    const existingMembership = await db
      .select()
      .from(schema.organizationMembers)
      .where(
        and(
          eq(schema.organizationMembers.organizationId, orgId),
          eq(schema.organizationMembers.userId, existingUser.id),
          eq(schema.organizationMembers.status, "ACTIVE")
        )
      )
      .get();

    if (existingMembership) {
      throw new AppError("User is already an active member of this organization", 409, "ALREADY_MEMBER");
    }
  }

  // 6. Prevent duplicate pending invitation
  const now = new Date();
  const inviteConditions = [
    eq(schema.organizationInvites.organizationId, orgId),
    eq(schema.organizationInvites.status, "PENDING"),
  ];

  if (existingUser) {
    const duplicate = await db
      .select()
      .from(schema.organizationInvites)
      .where(
        and(
          ...inviteConditions,
          or(
            eq(schema.organizationInvites.invitedUserId, existingUser.id),
            normalizedMobile ? eq(schema.organizationInvites.invitedMobile, normalizedMobile) : undefined
          )
        )
      )
      .get();

    if (duplicate && new Date(duplicate.expiresAt) > now) {
      throw new AppError("A pending invitation already exists for this recipient", 409, "DUPLICATE_INVITATION");
    }
  } else if (normalizedMobile) {
    const duplicate = await db
      .select()
      .from(schema.organizationInvites)
      .where(
        and(
          ...inviteConditions,
          eq(schema.organizationInvites.invitedMobile, normalizedMobile)
        )
      )
      .get();

    if (duplicate && new Date(duplicate.expiresAt) > now) {
      throw new AppError("A pending invitation already exists for this mobile number", 409, "DUPLICATE_INVITATION");
    }
  }

  // 7. Create organization invitation (7 days expiry)
  const inviteId = generateId("inv");
  const expiresAt = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);

  await db.insert(schema.organizationInvites)
    .values({
      id: inviteId,
      organizationId: orgId,
      invitedUserId: existingUser ? existingUser.id : null,
      invitedMobile: normalizedMobile || (email ?? ""),
      invitedEmail: email ?? null,
      invitedName: name ?? null,
      role,
      invitedBy: actorId,
      status: "PENDING",
      expiresAt,
      createdAt: now,
      updatedAt: now,
    })
    .run();

  // 8. Audit log
  await db.insert(schema.auditLogs)
    .values({
      id: generateId("aud"),
      organizationId: orgId,
      actorId,
      action: "staff.invited",
      resourceType: "organization_invite",
      resourceId: inviteId,
      metadataJson: JSON.stringify({ invitedMobile: normalizedMobile, email, role }),
      createdAt: now,
    })
    .run();

  // 9. Outbox event
  await db.insert(schema.outboxEvents)
    .values({
      id: generateId("evt"),
      organizationId: orgId,
      eventType: "staff.invited",
      payloadJson: JSON.stringify({
        organizationId: orgId,
        inviteId,
        invitedUserId: existingUser?.id ?? null,
        role,
      }),
      status: "PENDING",
      createdAt: now,
    })
    .run();

  // 10. In-app and Push Notification to recipient if user exists
  if (existingUser) {
    try {
      await db.insert(schema.notifications)
        .values({
          id: generateId("notif"),
          userId: existingUser.id,
          organizationId: orgId,
          title: "New Staff Invitation",
          message: `${org.name} invited you to join as ${role}`,
          type: "STAFF_INVITATION",
          isRead: false,
          metadataJson: JSON.stringify({
            organizationId: orgId,
            organizationName: org.name,
            inviteId,
            role,
            deepLink: `upieasy://organization/${orgId}/invitation/${inviteId}`,
          }),
          createdAt: now,
        })
        .run();

      // Find recipient's active devices
      const recipientDevices = await db
        .select()
        .from(schema.devices)
        .where(
          and(
            eq(schema.devices.userId, existingUser.id),
            eq(schema.devices.isActive, true)
          )
        )
        .all();

      for (const dev of recipientDevices) {
        if (dev.fcmToken) {
          await sendFcmToDevice(dev.fcmToken, {
            title: "New staff invitation",
            body: `${org.name}\nRole: ${role}\nTap to review`,
            data: {
              type: "staff.invited",
              organizationId: orgId,
              inviteId,
              role,
            },
          });
        }
      }
    } catch (err: any) {
      console.warn(`[Invite] Notification dispatch notice:`, err?.message);
    }
  }

  return c.json({
    success: true,
    message: "Invitation sent successfully",
    invite: {
      id: inviteId,
      organizationId: orgId,
      organizationName: org.name,
      role,
      status: "PENDING",
      expiresAt: expiresAt.toISOString(),
    },
  });
};

const directAddStaffHandler = async (c: any) => {
  const orgId = c.get("organizationId");
  const actorId = c.get("userId");
  const body = await c.req.json();

  const validator = z.object({
    mobileNumber: z.string().nullable().optional(),
    email: z.string().nullable().optional(),
    fullName: z.string().nullable().optional(),
    role: z.enum(["MANAGER", "CASHIER", "ACCOUNTANT"]),
  }).refine((data) => Boolean(data.mobileNumber || data.email), {
    message: "Either mobileNumber or email must be provided",
  });

  const parsed = validator.parse(body);
  const mobileNumber = parsed.mobileNumber?.trim() ? parsed.mobileNumber.trim() : undefined;
  const email = parsed.email?.trim() ? parsed.email.trim() : undefined;
  const fullName = parsed.fullName?.trim() ? parsed.fullName.trim() : undefined;
  const role = parsed.role;

  let roleRecord = await db.select().from(schema.roles).where(eq(schema.roles.name, role)).get();
  if (!roleRecord) {
    const standardRoles = [
      { id: "role_owner", name: "OWNER", description: "Full business control", isSystem: true },
      { id: "role_manager", name: "MANAGER", description: "Business and staff operations", isSystem: true },
      { id: "role_cashier", name: "CASHIER", description: "Payment initiation and transaction records", isSystem: true },
      { id: "role_accountant", name: "ACCOUNTANT", description: "Reconciliation, reporting and exports", isSystem: true },
    ];
    for (const r of standardRoles) {
      await db.insert(schema.roles).values(r).onConflictDoNothing().run();
    }
    roleRecord = await db.select().from(schema.roles).where(eq(schema.roles.name, role)).get();
  }

  let user: typeof schema.users.$inferSelect | undefined;
  if (email) {
    user = await db.select().from(schema.users).where(eq(schema.users.email, email.toLowerCase())).get();
  }
  if (!user && mobileNumber) {
    user = await db.select().from(schema.users).where(eq(schema.users.mobileNumber, mobileNumber)).get();
  }
  const now = new Date();

  if (!user) {
    const newUserId = generateId("usr");
    await db.insert(schema.users)
      .values({
        id: newUserId,
        mobileNumber: mobileNumber ?? null,
        email: email ? email.toLowerCase() : null,
        fullName: fullName ?? null,
        status: "ACTIVE",
        createdAt: now,
        updatedAt: now,
      })
      .run();
    user = (await db.select().from(schema.users).where(eq(schema.users.id, newUserId)).get())!;
  }

  const existingMember = await db
    .select()
    .from(schema.organizationMembers)
    .where(
      and(
        eq(schema.organizationMembers.organizationId, orgId),
        eq(schema.organizationMembers.userId, user!.id)
      )
    )
    .get();

  if (existingMember) {
    throw new AppError("User is already a member of this organization", 409, "ALREADY_MEMBER");
  }

  const memberId = generateId("mem");
  await db.insert(schema.organizationMembers)
    .values({
      id: memberId,
      organizationId: orgId,
      userId: user!.id,
      roleId: roleRecord!.id,
      status: "ACTIVE",
      invitedBy: actorId,
      joinedAt: now,
      createdAt: now,
      updatedAt: now,
    })
    .run();

  return c.json({
    success: true,
    message: "Staff member added successfully",
    member: {
      id: memberId,
      mobileNumber: user!.mobileNumber,
      role,
      status: "ACTIVE",
    },
  });
};

membersRouter.post("/:orgId/invites", requireTenant, requirePermission("staff.manage"), createInviteHandler);
membersRouter.post("/:orgId/staff/invite", requireTenant, requirePermission("staff.manage"), directAddStaffHandler);

membersRouter.patch(
  "/:orgId/staff/:memberId",
  requireTenant,
  requirePermission("staff.manage"),
  async (c) => {
    const orgId = c.get("organizationId");
    const actorId = c.get("userId");
    const memberId = c.req.param("memberId");
    const body = await c.req.json();

    const validator = z.object({
      role: z.enum(["MANAGER", "CASHIER", "ACCOUNTANT"]).optional(),
      status: z.enum(["ACTIVE", "SUSPENDED"]).optional(),
    });

    const { role, status } = validator.parse(body);

    const member = await db
      .select()
      .from(schema.organizationMembers)
      .where(
        and(
          eq(schema.organizationMembers.id, memberId),
          eq(schema.organizationMembers.organizationId, orgId)
        )
      )
      .get();

    if (!member) {
      throw new NotFoundError("Staff member not found");
    }

    let newRoleId = member.roleId;
    if (role) {
      const roleRecord = await db.select().from(schema.roles).where(eq(schema.roles.name, role)).get();
      if (!roleRecord) throw new AppError("Invalid role");
      newRoleId = roleRecord.id;
    }

    await db.update(schema.organizationMembers)
      .set({
        roleId: newRoleId,
        status: status ?? member.status,
        updatedAt: new Date(),
      })
      .where(eq(schema.organizationMembers.id, memberId))
      .run();

    // Audit log
    await db.insert(schema.auditLogs)
      .values({
        id: generateId("aud"),
        organizationId: orgId,
        actorId,
        action: "role.changed",
        resourceType: "organization_member",
        resourceId: memberId,
        metadataJson: JSON.stringify({ newRole: role, newStatus: status }),
        createdAt: new Date(),
      })
      .run();

    return c.json({ success: true, message: "Staff member updated successfully" });
  }
);

membersRouter.delete(
  "/:orgId/staff/:memberId",
  requireTenant,
  requirePermission("staff.manage"),
  async (c) => {
    const orgId = c.get("organizationId");
    const actorId = c.get("userId");
    const memberId = c.req.param("memberId");

    const member = await db
      .select()
      .from(schema.organizationMembers)
      .where(
        and(
          eq(schema.organizationMembers.id, memberId),
          eq(schema.organizationMembers.organizationId, orgId)
        )
      )
      .get();

    if (!member) {
      throw new NotFoundError("Staff member not found");
    }

    await db.delete(schema.organizationMembers).where(eq(schema.organizationMembers.id, memberId)).run();

    await db.insert(schema.auditLogs)
      .values({
        id: generateId("aud"),
        organizationId: orgId,
        actorId,
        action: "staff.removed",
        resourceType: "organization_member",
        resourceId: memberId,
        createdAt: new Date(),
      })
      .run();

    return c.json({ success: true, message: "Staff member removed successfully" });
  }
);
