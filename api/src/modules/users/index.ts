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
  const user = db.select().from(schema.users).where(eq(schema.users.id, userId)).get();

  if (!user) {
    throw new NotFoundError("User not found");
  }

  // Fetch organizations where user is a member
  const memberships = db
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

usersRouter.get("/me", getMeHandler);
usersRouter.get("/", getMeHandler);

const patchMeHandler = async (c: any) => {
  const userId = c.get("userId");
  const body = await c.req.json();
  const schemaValidator = z.object({
    fullName: z.string().min(2).optional(),
    email: z.string().email().optional(),
  });

  const { fullName, email } = schemaValidator.parse(body);

  db.update(schema.users)
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
  db.update(schema.sessions)
    .set({ isRevoked: true, updatedAt: new Date() })
    .where(eq(schema.sessions.userId, userId))
    .run();

  // 2. Remove organization memberships
  db.delete(schema.organizationMembers)
    .where(eq(schema.organizationMembers.userId, userId))
    .run();

  // 3. Delete devices & user record (cascading to devices & sessions)
  db.delete(schema.users).where(eq(schema.users.id, userId)).run();

  return c.json({
    success: true,
    message: "User account and all associated sessions deleted successfully",
  });
};

usersRouter.delete("/me", deleteMeHandler);
usersRouter.delete("/", deleteMeHandler);
