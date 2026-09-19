import type { MiddlewareHandler } from "hono";
import { ForbiddenError } from "../lib/errors.js";
import { db } from "../db/index.js";
import * as schema from "../db/schema/index.js";
import { eq, and } from "drizzle-orm";
import type { AppEnv } from "../types/hono.js";

export const requireTenant: MiddlewareHandler<AppEnv> = async (c, next) => {
  const userId = c.get("userId");
  const orgId = c.req.param("orgId") || c.req.header("x-organization-id");

  if (!orgId) {
    throw new ForbiddenError("Missing organization context");
  }

  // Check membership
  const member = await db
    .select({
      memberId: schema.organizationMembers.id,
      organizationId: schema.organizationMembers.organizationId,
      userId: schema.organizationMembers.userId,
      roleId: schema.organizationMembers.roleId,
      status: schema.organizationMembers.status,
      roleName: schema.roles.name,
    })
    .from(schema.organizationMembers)
    .innerJoin(schema.roles, eq(schema.organizationMembers.roleId, schema.roles.id))
    .where(
      and(
        eq(schema.organizationMembers.organizationId, orgId),
        eq(schema.organizationMembers.userId, userId),
        eq(schema.organizationMembers.status, "ACTIVE")
      )
    )
    .get();

  if (!member) {
    throw new ForbiddenError("User is not an active member of this organization");
  }

  // Fetch permissions for this role
  const permissionsRows = await db
    .select({
      name: schema.permissions.name,
    })
    .from(schema.rolePermissions)
    .innerJoin(schema.permissions, eq(schema.rolePermissions.permissionId, schema.permissions.id))
    .where(eq(schema.rolePermissions.roleId, member.roleId))
    .all();

  const permissions = permissionsRows.map((p: any) => p.name);

  c.set("organizationId", orgId);
  c.set("memberId", member.memberId);
  c.set("role", member.roleName);
  c.set("permissions", permissions);

  await next();
};
