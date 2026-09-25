import { Hono } from "hono";
import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq, and, or } from "drizzle-orm";
import { generateId } from "../../lib/crypto.js";
import { requireAuth } from "../../middleware/auth.js";
import { AppError, NotFoundError, ForbiddenError } from "../../lib/errors.js";
import { get10DigitMobile, normalizeIndianMobileNumber } from "../../lib/phone.js";
import { notifyOrganizationMembers } from "../../lib/fcm.js";
import type { AppEnv } from "../../types/hono.js";

export const invitationsRouter = new Hono<AppEnv>();

/**
 * GET /api/v1/invitations/verify/:token
 * Public endpoint to verify and display invitation details before accepting.
 */
invitationsRouter.get("/verify/:token", async (c) => {
  const token = c.req.param("token");
  const now = new Date();

  const invite = await db
    .select({
      id: schema.organizationInvites.id,
      token: schema.organizationInvites.token,
      organizationId: schema.organizationInvites.organizationId,
      organizationName: schema.organizations.name,
      role: schema.organizationInvites.role,
      status: schema.organizationInvites.status,
      invitedEmail: schema.organizationInvites.invitedEmail,
      invitedName: schema.organizationInvites.invitedName,
      expiresAt: schema.organizationInvites.expiresAt,
      createdAt: schema.organizationInvites.createdAt,
    })
    .from(schema.organizationInvites)
    .innerJoin(schema.organizations, eq(schema.organizationInvites.organizationId, schema.organizations.id))
    .where(
      or(
        eq(schema.organizationInvites.token, token),
        eq(schema.organizationInvites.id, token)
      )
    )
    .get();

  if (!invite) {
    throw new NotFoundError("Invitation not found");
  }

  const isExpired = new Date(invite.expiresAt) <= now;
  const isValid = invite.status === "PENDING" && !isExpired;

  return c.json({
    success: true,
    isValid,
    invite: {
      ...invite,
      status: isExpired ? "EXPIRED" : invite.status,
    },
  });
});

invitationsRouter.use("*", requireAuth);

/**
 * GET /api/v1/invitations/me OR GET /api/v1/me/invitations
 * Returns pending invitations sent to the authenticated user (by userId or mobile).
 */
export const getMyInvitationsHandler = async (c: any) => {
  const userId = c.get("userId");
  const user = await db.select().from(schema.users).where(eq(schema.users.id, userId)).get();

  if (!user) {
    throw new NotFoundError("User not found");
  }

  const userMobile10 = user.mobileNumber ? get10DigitMobile(user.mobileNumber) : null;
  const userMobileFull = user.mobileNumber ? normalizeIndianMobileNumber(user.mobileNumber) : null;

  // Query invites where invitedUserId = userId OR invitedEmail matches user's email OR invitedMobile matches user's mobile
  const conditions = [eq(schema.organizationInvites.invitedUserId, userId)];
  if (user.email) {
    conditions.push(eq(schema.organizationInvites.invitedEmail, user.email.toLowerCase()));
  }
  if (userMobileFull) {
    conditions.push(eq(schema.organizationInvites.invitedMobile, userMobileFull));
  }
  if (userMobile10) {
    conditions.push(eq(schema.organizationInvites.invitedMobile, userMobile10));
  }

  const invites = await db
    .select({
      id: schema.organizationInvites.id,
      token: schema.organizationInvites.token,
      organizationId: schema.organizationInvites.organizationId,
      organizationName: schema.organizations.name,
      role: schema.organizationInvites.role,
      status: schema.organizationInvites.status,
      invitedMobile: schema.organizationInvites.invitedMobile,
      invitedName: schema.organizationInvites.invitedName,
      invitedEmail: schema.organizationInvites.invitedEmail,
      expiresAt: schema.organizationInvites.expiresAt,
      createdAt: schema.organizationInvites.createdAt,
      inviterName: schema.users.fullName,
      inviterEmail: schema.users.email,
    })
    .from(schema.organizationInvites)
    .innerJoin(schema.organizations, eq(schema.organizationInvites.organizationId, schema.organizations.id))
    .leftJoin(schema.users, eq(schema.organizationInvites.invitedBy, schema.users.id))
    .where(
      and(
        or(...conditions),
        eq(schema.organizationInvites.status, "PENDING")
      )
    )
    .all();

  // Filter out expired invites in-memory and mark them if needed
  const now = new Date();
  const validInvites = invites.filter((inv: any) => new Date(inv.expiresAt) > now);

  return c.json({
    success: true,
    invitations: validInvites,
  });
};

invitationsRouter.get("/me", getMyInvitationsHandler);
invitationsRouter.get("/", getMyInvitationsHandler);

/**
 * POST /api/v1/invitations/:inviteId/accept
 * Atomically accepts an invitation and adds user to organization_members.
 */
invitationsRouter.post("/:inviteId/accept", async (c) => {
  const inviteId = c.req.param("inviteId");
  const userId = c.get("userId");

  const user = await db.select().from(schema.users).where(eq(schema.users.id, userId)).get();
  if (!user) {
    throw new NotFoundError("User not found");
  }

  const invite = await db
    .select()
    .from(schema.organizationInvites)
    .where(
      or(
        eq(schema.organizationInvites.id, inviteId),
        eq(schema.organizationInvites.token, inviteId)
      )
    )
    .get();

  if (!invite) {
    throw new NotFoundError("Invitation not found");
  }

  // Verification: Is this invite addressed to this user?
  const userMobile10 = user.mobileNumber ? get10DigitMobile(user.mobileNumber) : null;
  const userMobileFull = user.mobileNumber ? normalizeIndianMobileNumber(user.mobileNumber) : null;
  const inviteMobile = invite.invitedMobile;

  const matchesUser =
    invite.invitedUserId === userId ||
    (invite.invitedEmail && user.email && invite.invitedEmail.toLowerCase() === user.email.toLowerCase()) ||
    (userMobileFull && inviteMobile && inviteMobile === userMobileFull) ||
    (userMobile10 && inviteMobile && (inviteMobile === userMobile10 || inviteMobile === `+91${userMobile10}`));

  if (!matchesUser) {
    throw new ForbiddenError("You are not authorized to accept this invitation");
  }

  if (invite.status !== "PENDING") {
    throw new AppError(`Invitation is already ${invite.status.toLowerCase()}`, 409, "INVITE_NOT_PENDING");
  }

  const now = new Date();
  if (new Date(invite.expiresAt) <= now) {
    await db.update(schema.organizationInvites)
      .set({ status: "EXPIRED", updatedAt: now })
      .where(eq(schema.organizationInvites.id, inviteId))
      .run();
    throw new AppError("Invitation has expired", 409, "INVITE_EXPIRED");
  }

  // Find or ensure target role exists
  let roleRecord = await db.select().from(schema.roles).where(eq(schema.roles.name, invite.role)).get();
  if (!roleRecord) {
    const roleMap: Record<string, string> = {
      OWNER: "role_owner",
      MANAGER: "role_manager",
      CASHIER: "role_cashier",
      ACCOUNTANT: "role_accountant",
    };
    const roleId = roleMap[invite.role] || `role_${invite.role.toLowerCase()}`;
    await db.insert(schema.roles)
      .values({
        id: roleId,
        name: invite.role,
        description: `${invite.role} role`,
        isSystem: true,
      })
      .onConflictDoNothing()
      .run();
    roleRecord = await db.select().from(schema.roles).where(eq(schema.roles.name, invite.role)).get();
  }

  if (!roleRecord) {
    throw new AppError("Invalid role configured for invitation", 500);
  }

  // Check if member already exists
  const existingMember = await db
    .select()
    .from(schema.organizationMembers)
    .where(
      and(
        eq(schema.organizationMembers.organizationId, invite.organizationId),
        eq(schema.organizationMembers.userId, userId)
      )
    )
    .get();

  if (existingMember) {
    await db.update(schema.organizationMembers)
      .set({
        roleId: roleRecord.id,
        status: "ACTIVE",
        joinedAt: now,
        updatedAt: now,
      })
      .where(eq(schema.organizationMembers.id, existingMember.id))
      .run();
  } else {
    const memberId = generateId("mem");
    await db.insert(schema.organizationMembers)
      .values({
        id: memberId,
        organizationId: invite.organizationId,
        userId: userId,
        roleId: roleRecord.id,
        status: "ACTIVE",
        invitedBy: invite.invitedBy,
        joinedAt: now,
        createdAt: now,
        updatedAt: now,
      })
      .run();
  }

  // Mark invite as ACCEPTED
  await db.update(schema.organizationInvites)
    .set({
      status: "ACCEPTED",
      invitedUserId: userId,
      acceptedAt: now,
      updatedAt: now,
    })
    .where(eq(schema.organizationInvites.id, inviteId))
    .run();

  // Create Outbox Event
  await db.insert(schema.outboxEvents)
    .values({
      id: generateId("evt"),
      organizationId: invite.organizationId,
      eventType: "staff.joined",
      payloadJson: JSON.stringify({
        userId,
        role: invite.role,
        organizationId: invite.organizationId,
      }),
      status: "PENDING",
      createdAt: now,
    })
    .run();

  // Audit log
  await db.insert(schema.auditLogs)
    .values({
      id: generateId("aud"),
      organizationId: invite.organizationId,
      actorId: userId,
      action: "staff.invite.accepted",
      resourceType: "organization_invite",
      resourceId: inviteId,
      metadataJson: JSON.stringify({ role: invite.role, userId }),
      createdAt: now,
    })
    .run();

  // Notify organization members of new staff join
  await notifyOrganizationMembers({
    organizationId: invite.organizationId,
    type: "staff.joined",
    title: "New Staff Joined",
    body: `${user.fullName || "A new member"} joined as ${invite.role}`,
    data: {
      userId,
      role: invite.role,
      organizationId: invite.organizationId,
    },
    excludeUserId: userId,
  });

  // Fetch updated organization details to return
  const org = await db.select().from(schema.organizations).where(eq(schema.organizations.id, invite.organizationId)).get();

  return c.json({
    success: true,
    message: "Invitation accepted successfully",
    organization: {
      id: org?.id,
      name: org?.name,
      role: invite.role,
      status: "ACTIVE",
    },
  });
});

/**
 * POST /api/v1/invitations/:inviteId/reject
 */
invitationsRouter.post("/:inviteId/reject", async (c) => {
  const inviteId = c.req.param("inviteId");
  const userId = c.get("userId");

  const user = await db.select().from(schema.users).where(eq(schema.users.id, userId)).get();
  if (!user) throw new NotFoundError("User not found");

  const invite = await db.select().from(schema.organizationInvites).where(eq(schema.organizationInvites.id, inviteId)).get();
  if (!invite) throw new NotFoundError("Invitation not found");

  const userMobile10 = user.mobileNumber ? get10DigitMobile(user.mobileNumber) : null;
  const userMobileFull = user.mobileNumber ? normalizeIndianMobileNumber(user.mobileNumber) : null;

  const matchesUser =
    invite.invitedUserId === userId ||
    (userMobileFull && invite.invitedMobile === userMobileFull) ||
    (userMobile10 && (invite.invitedMobile === userMobile10 || invite.invitedMobile === `+91${userMobile10}`));

  if (!matchesUser) {
    throw new ForbiddenError("You are not authorized to reject this invitation");
  }

  if (invite.status !== "PENDING") {
    throw new AppError(`Invitation is already ${invite.status.toLowerCase()}`, 409, "INVITE_NOT_PENDING");
  }

  const now = new Date();
  await db.update(schema.organizationInvites)
    .set({
      status: "REJECTED",
      rejectedAt: now,
      updatedAt: now,
    })
    .where(eq(schema.organizationInvites.id, inviteId))
    .run();

  await db.insert(schema.auditLogs)
    .values({
      id: generateId("aud"),
      organizationId: invite.organizationId,
      actorId: userId,
      action: "staff.invite.rejected",
      resourceType: "organization_invite",
      resourceId: inviteId,
      createdAt: now,
    })
    .run();

  return c.json({
    success: true,
    message: "Invitation rejected",
  });
});

/**
 * POST /api/v1/invitations/:inviteId/cancel
 * Cancelled by the sending organization's managers.
 */
invitationsRouter.post("/:inviteId/cancel", async (c) => {
  const inviteId = c.req.param("inviteId");
  const userId = c.get("userId");

  const invite = await db.select().from(schema.organizationInvites).where(eq(schema.organizationInvites.id, inviteId)).get();
  if (!invite) throw new NotFoundError("Invitation not found");

  // Check user has staff.manage permission or is the inviter
  const member = await db
    .select()
    .from(schema.organizationMembers)
    .where(
      and(
        eq(schema.organizationMembers.organizationId, invite.organizationId),
        eq(schema.organizationMembers.userId, userId),
        eq(schema.organizationMembers.status, "ACTIVE")
      )
    )
    .get();

  if (!member && invite.invitedBy !== userId) {
    throw new ForbiddenError("You do not have permission to cancel this invitation");
  }

  const now = new Date();
  await db.delete(schema.organizationInvites)
    .where(eq(schema.organizationInvites.id, inviteId))
    .run();

  await db.insert(schema.auditLogs)
    .values({
      id: generateId("aud"),
      organizationId: invite.organizationId,
      actorId: userId,
      action: "staff.invite.cancelled",
      resourceType: "organization_invite",
      resourceId: inviteId,
      createdAt: now,
    })
    .run();

  return c.json({
    success: true,
    message: "Invitation cancelled",
  });
});
