import { Hono } from "hono";
import { z } from "zod";
import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq, and } from "drizzle-orm";
import { generateId } from "../../lib/crypto.js";
import { requireAuth } from "../../middleware/auth.js";
import { requireTenant } from "../../middleware/tenant.js";
import { requirePermission } from "../../middleware/rbac.js";
import { AppError, NotFoundError } from "../../lib/errors.js";
import type { AppEnv } from "../../types/hono.js";

export const membersRouter = new Hono<AppEnv>();

membersRouter.use("*", requireAuth);

membersRouter.get("/:orgId/staff", requireTenant, requirePermission("staff.read"), async (c) => {
  const orgId = c.get("organizationId");

  const staff = db
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
    .innerJoin(schema.roles, eq(schema.organizationMembers.roleId, schema.roles.id))
    .where(eq(schema.organizationMembers.organizationId, orgId))
    .all();

  return c.json({ success: true, staff });
});

membersRouter.post("/:orgId/staff/invite", requireTenant, requirePermission("staff.manage"), async (c) => {
  const orgId = c.get("organizationId");
  const actorId = c.get("userId");
  const body = await c.req.json();

  const validator = z.object({
    mobileNumber: z.string().regex(/^[6-9]\d{9}$/).optional(),
    email: z.string().email().optional(),
    fullName: z.string().optional(),
    role: z.enum(["MANAGER", "CASHIER", "ACCOUNTANT"]),
  }).refine((data) => data.mobileNumber || data.email, {
    message: "Either mobileNumber or email must be provided",
  });

  const { mobileNumber, email, fullName, role } = validator.parse(body);

  // Find target role
  const roleRecord = db.select().from(schema.roles).where(eq(schema.roles.name, role)).get();
  if (!roleRecord) {
    throw new AppError("Invalid role specified", 400);
  }

  // Find or create the user by email or mobileNumber
  let user: typeof schema.users.$inferSelect | undefined;
  if (email) {
    user = db.select().from(schema.users).where(eq(schema.users.email, email.toLowerCase())).get();
  }
  if (!user && mobileNumber) {
    user = db.select().from(schema.users).where(eq(schema.users.mobileNumber, mobileNumber)).get();
  }
  const now = new Date();

  if (!user) {
    const newUserId = generateId("usr");
    db.insert(schema.users)
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
    user = db.select().from(schema.users).where(eq(schema.users.id, newUserId)).get()!;
  }

  if (!user) {
    throw new AppError("Failed to create or find user", 500);
  }

  // Check if already a member
  const existingMember = db
    .select()
    .from(schema.organizationMembers)
    .where(
      and(
        eq(schema.organizationMembers.organizationId, orgId),
        eq(schema.organizationMembers.userId, user.id)
      )
    )
    .get();

  if (existingMember) {
    throw new AppError("User is already a member of this organization", 409, "ALREADY_MEMBER");
  }

  const memberId = generateId("mem");
  db.insert(schema.organizationMembers)
    .values({
      id: memberId,
      organizationId: orgId,
      userId: user.id,
      roleId: roleRecord.id,
      status: "ACTIVE",
      invitedBy: actorId,
      joinedAt: now,
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
      action: "staff.invited",
      resourceType: "organization_member",
      resourceId: memberId,
      metadataJson: JSON.stringify({ invitedMobile: mobileNumber, role }),
      createdAt: now,
    })
    .run();

  // Outbox event
  db.insert(schema.outboxEvents)
    .values({
      id: generateId("evt"),
      organizationId: orgId,
      eventType: "member.invited",
      payloadJson: JSON.stringify({ organizationId: orgId, memberId, role }),
      status: "PENDING",
      createdAt: now,
    })
    .run();

  return c.json({
    success: true,
    message: "Staff member added successfully",
    member: {
      id: memberId,
      mobileNumber: user.mobileNumber,
      role,
      status: "ACTIVE",
    },
  });
});

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

    const member = db
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
      const roleRecord = db.select().from(schema.roles).where(eq(schema.roles.name, role)).get();
      if (!roleRecord) throw new AppError("Invalid role");
      newRoleId = roleRecord.id;
    }

    db.update(schema.organizationMembers)
      .set({
        roleId: newRoleId,
        status: status ?? member.status,
        updatedAt: new Date(),
      })
      .where(eq(schema.organizationMembers.id, memberId))
      .run();

    // Audit log
    db.insert(schema.auditLogs)
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

    const member = db
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

    db.delete(schema.organizationMembers).where(eq(schema.organizationMembers.id, memberId)).run();

    db.insert(schema.auditLogs)
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
