import { Hono } from "hono";
import { z } from "zod";
import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq } from "drizzle-orm";
import { requireAuth } from "../../middleware/auth.js";
import { NotFoundError } from "../../lib/errors.js";
import type { AppEnv } from "../../types/hono.js";

export const usersRouter = new Hono<AppEnv>();

usersRouter.use("*", requireAuth);

const getMeHandler = async (c: any) => {
  const userId = c.get("userId");
  const user = await db.select().from(schema.users).where(eq(schema.users.id, userId)).get();

  if (!user) {
    throw new NotFoundError("User not found");
  }

  // Fetch organizations where user is a member
  const memberships = await db
    .select({
      organizationId: schema.organizations.id,
      organizationName: schema.organizations.name,
      legalBusinessName: schema.organizations.legalBusinessName,
      category: schema.organizations.category,
      roleName: schema.roles.name,
      memberStatus: schema.organizationMembers.status,
    })
    .from(schema.organizationMembers)
    .innerJoin(schema.organizations, eq(schema.organizationMembers.organizationId, schema.organizations.id))
    .innerJoin(schema.roles, eq(schema.organizationMembers.roleId, schema.roles.id))
    .where(eq(schema.organizationMembers.userId, userId))
    .all();

  return c.json({
    success: true,
    user: {
      id: user.id,
      mobileNumber: user.mobileNumber,
      fullName: user.fullName,
      email: user.email,
      status: user.status,
      createdAt: user.createdAt,
    },
    organizations: memberships,
  });
};

import { getMyInvitationsHandler } from "../invitations/index.js";

usersRouter.get("/me", getMeHandler);
usersRouter.get("/invitations", getMyInvitationsHandler);
usersRouter.get("/organizations", async (c: any) => {
  const userId = c.get("userId");
  const memberships = await db
    .select({
      id: schema.organizations.id,
      organizationId: schema.organizations.id,
      name: schema.organizations.name,
      organizationName: schema.organizations.name,
      legalBusinessName: schema.organizations.legalBusinessName,
      category: schema.organizations.category,
      roleId: schema.organizationMembers.roleId,
      role: schema.roles.name,
      roleName: schema.roles.name,
      status: schema.organizationMembers.status,
      memberStatus: schema.organizationMembers.status,
    })
    .from(schema.organizationMembers)
    .innerJoin(schema.organizations, eq(schema.organizationMembers.organizationId, schema.organizations.id))
    .leftJoin(schema.roles, eq(schema.organizationMembers.roleId, schema.roles.id))
    .where(eq(schema.organizationMembers.userId, userId))
    .all();

  const orgsWithPermissions = await Promise.all(
    memberships.map(async (m: any) => {
      const role = m.role || "MEMBER";
      let permissions: string[] = [];
      if (role === "OWNER") {
        permissions = ["*"];
      } else if (m.roleId) {
        const permRows = await db
          .select({ name: schema.permissions.name })
          .from(schema.rolePermissions)
          .innerJoin(schema.permissions, eq(schema.rolePermissions.permissionId, schema.permissions.id))
          .where(eq(schema.rolePermissions.roleId, m.roleId))
          .all();
        permissions = permRows.map((p: { name: string }) => p.name);
      }
      return {
        ...m,
        role,
        roleName: role,
        permissions,
      };
    })
  );

  return c.json({
    success: true,
    organizations: orgsWithPermissions,
  });
});
usersRouter.get("/", getMeHandler);

const patchMeHandler = async (c: any) => {
  const userId = c.get("userId");
  const body = await c.req.json();
  const schemaValidator = z.object({
    fullName: z.string().min(2).optional(),
    email: z.string().email().optional(),
    avatarUrl: z.string().optional(),
  });

  const { fullName, email, avatarUrl } = schemaValidator.parse(body);

  await db.update(schema.users)
    .set({
      fullName: fullName ?? undefined,
      email: email ?? undefined,
      avatarUrl: avatarUrl ?? undefined,
      updatedAt: new Date(),
    })
    .where(eq(schema.users.id, userId))
    .run();

  return c.json({ success: true, message: "Profile updated successfully" });
};

usersRouter.patch("/me", patchMeHandler);
usersRouter.patch("/", patchMeHandler);

const deleteMeHandler = async (c: any) => {
  const userId = c.get("userId");

  try {
    // 1. Find organizations owned by this user
    const ownedOrgs = await db.select({ id: schema.organizations.id }).from(schema.organizations).where(eq(schema.organizations.ownerId, userId)).all();
    for (const org of ownedOrgs) {
      const orgId = org.id;
      // Delete transaction events & references first
      const orgTxs = await db.select({ id: schema.transactions.id }).from(schema.transactions).where(eq(schema.transactions.organizationId, orgId)).all();
      for (const tx of orgTxs) {
        await db.delete(schema.transactionEvents).where(eq(schema.transactionEvents.transactionId, tx.id)).run();
        await db.delete(schema.transactionReferences).where(eq(schema.transactionReferences.transactionId, tx.id)).run();
      }
      await db.delete(schema.transactions).where(eq(schema.transactions.organizationId, orgId)).run();
      await db.delete(schema.observedPaymentEvents).where(eq(schema.observedPaymentEvents.organizationId, orgId)).run();
      await db.delete(schema.paymentAccounts).where(eq(schema.paymentAccounts.organizationId, orgId)).run();
      await db.delete(schema.upiAccounts).where(eq(schema.upiAccounts.organizationId, orgId)).run();
      await db.delete(schema.bankAccounts).where(eq(schema.bankAccounts.organizationId, orgId)).run();
      await db.delete(schema.qrCodes).where(eq(schema.qrCodes.organizationId, orgId)).run();
      await db.delete(schema.organizationInvites).where(eq(schema.organizationInvites.organizationId, orgId)).run();
      await db.delete(schema.organizationMembers).where(eq(schema.organizationMembers.organizationId, orgId)).run();
      await db.delete(schema.notifications).where(eq(schema.notifications.organizationId, orgId)).run();
      await db.delete(schema.notificationPreferences).where(eq(schema.notificationPreferences.organizationId, orgId)).run();
      await db.delete(schema.auditLogs).where(eq(schema.auditLogs.organizationId, orgId)).run();
      await db.delete(schema.idempotencyKeys).where(eq(schema.idempotencyKeys.organizationId, orgId)).run();
      await db.delete(schema.outboxEvents).where(eq(schema.outboxEvents.organizationId, orgId)).run();
      await db.delete(schema.syncCursors).where(eq(schema.syncCursors.organizationId, orgId)).run();
      await db.delete(schema.organizations).where(eq(schema.organizations.id, orgId)).run();
    }

    // 2. Remove references from any other organizations where this user was a member/invitee
    await db.delete(schema.organizationMembers).where(eq(schema.organizationMembers.userId, userId)).run();
    await db.update(schema.organizationMembers).set({ invitedBy: null }).where(eq(schema.organizationMembers.invitedBy, userId)).run();
    await db.delete(schema.organizationInvites).where(eq(schema.organizationInvites.invitedUserId, userId)).run();
    await db.delete(schema.organizationInvites).where(eq(schema.organizationInvites.invitedBy, userId)).run();
    await db.update(schema.auditLogs).set({ actorId: null }).where(eq(schema.auditLogs.actorId, userId)).run();
    await db.delete(schema.notifications).where(eq(schema.notifications.userId, userId)).run();
    await db.delete(schema.notificationPreferences).where(eq(schema.notificationPreferences.userId, userId)).run();
    await db.delete(schema.syncCursors).where(eq(schema.syncCursors.userId, userId)).run();
    await db.delete(schema.sessions).where(eq(schema.sessions.userId, userId)).run();
    await db.delete(schema.devices).where(eq(schema.devices.userId, userId)).run();

    // 3. Delete user record
    await db.delete(schema.users).where(eq(schema.users.id, userId)).run();

    return c.json({
      success: true,
      message: "User account and all associated sessions deleted successfully",
    });
  } catch (err: any) {
    return c.json(
      {
        success: false,
        error: {
          code: "DELETION_FAILED",
          message: err.message || "Failed to delete account completely",
        },
      },
      500
    );
  }
};

usersRouter.delete("/me", deleteMeHandler);
usersRouter.delete("/", deleteMeHandler);

