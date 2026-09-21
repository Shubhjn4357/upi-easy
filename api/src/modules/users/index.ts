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
      role: schema.roles.name,
      roleName: schema.roles.name,
      status: schema.organizationMembers.status,
      memberStatus: schema.organizationMembers.status,
    })
    .from(schema.organizationMembers)
    .innerJoin(schema.organizations, eq(schema.organizationMembers.organizationId, schema.organizations.id))
    .innerJoin(schema.roles, eq(schema.organizationMembers.roleId, schema.roles.id))
    .where(eq(schema.organizationMembers.userId, userId))
    .all();

  return c.json({
    success: true,
    organizations: memberships,
  });
});
usersRouter.get("/", getMeHandler);

const patchMeHandler = async (c: any) => {
  const userId = c.get("userId");
  const body = await c.req.json();
  const schemaValidator = z.object({
    fullName: z.string().min(2).optional(),
    email: z.string().email().optional(),
  });

  const { fullName, email } = schemaValidator.parse(body);

  await db.update(schema.users)
    .set({
      fullName: fullName ?? undefined,
      email: email ?? undefined,
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

  // 1. Revoke all active sessions
  await db.update(schema.sessions)
    .set({ isRevoked: true, updatedAt: new Date() })
    .where(eq(schema.sessions.userId, userId))
    .run();

  // 2. Handle organizations owned by this user
  const ownedOrgs = await db.select().from(schema.organizations).where(eq(schema.organizations.ownerId, userId)).all();
  for (const org of ownedOrgs) {
    try {
      await db.delete(schema.transactions).where(eq(schema.transactions.organizationId, org.id)).run();
      await db.delete(schema.upiAccounts).where(eq(schema.upiAccounts.organizationId, org.id)).run();
      await db.delete(schema.bankAccounts).where(eq(schema.bankAccounts.organizationId, org.id)).run();
      await db.delete(schema.qrCodes).where(eq(schema.qrCodes.organizationId, org.id)).run();
      await db.delete(schema.organizationMembers).where(eq(schema.organizationMembers.organizationId, org.id)).run();
      await db.delete(schema.auditLogs).where(eq(schema.auditLogs.organizationId, org.id)).run();
      await db.delete(schema.idempotencyKeys).where(eq(schema.idempotencyKeys.organizationId, org.id)).run();
      await db.delete(schema.organizations).where(eq(schema.organizations.id, org.id)).run();
    } catch (_: any) {}
  }

  // 3. Clear audit logs and member invites where this user is referenced
  try {
    await db.update(schema.auditLogs).set({ actorId: null }).where(eq(schema.auditLogs.actorId, userId)).run();
    await db.update(schema.organizationMembers).set({ invitedBy: null }).where(eq(schema.organizationMembers.invitedBy, userId)).run();
  } catch (_: any) {}

  // 4. Remove organization memberships
  await db.delete(schema.organizationMembers)
    .where(eq(schema.organizationMembers.userId, userId))
    .run();

  // 5. Delete devices & user record (cascading to devices & sessions)
  try {
    await db.delete(schema.devices).where(eq(schema.devices.userId, userId)).run();
    await db.delete(schema.sessions).where(eq(schema.sessions.userId, userId)).run();
  } catch (_: any) {}

  await db.delete(schema.users).where(eq(schema.users.id, userId)).run();

  return c.json({
    success: true,
    message: "User account and all associated sessions deleted successfully",
  });
};

usersRouter.delete("/me", deleteMeHandler);
usersRouter.delete("/", deleteMeHandler);
