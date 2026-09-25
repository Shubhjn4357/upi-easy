import { Hono } from "hono";
import { z } from "zod";
import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq, and } from "drizzle-orm";
import { requireAuth } from "../../middleware/auth.js";
import { requireTenant } from "../../middleware/tenant.js";
import { requirePermission } from "../../middleware/rbac.js";
import { AppError, ForbiddenError, NotFoundError } from "../../lib/errors.js";
import type { AppEnv } from "../../types/hono.js";

export const rolesRouter = new Hono<AppEnv>();

rolesRouter.use("*", requireAuth);

// GET /api/v1/organizations/:orgId/roles
// Fetch all system roles, all permissions, and role-permission mappings
rolesRouter.get("/:orgId/roles", requireTenant, async (c) => {
  const allRoles = await db.select().from(schema.roles).all();
  const allPermissions = await db.select().from(schema.permissions).all();
  const allRolePerms = await db.select().from(schema.rolePermissions).all();

  const formattedRoles = allRoles.map((role) => {
    let permIds: string[] = [];
    if (role.name === "OWNER") {
      permIds = allPermissions.map((p) => p.id);
    } else {
      permIds = allRolePerms
        .filter((rp) => rp.roleId === role.id)
        .map((rp) => rp.permissionId);
    }

    return {
      id: role.id,
      name: role.name,
      description: role.description,
      isSystem: role.isSystem,
      permissions: permIds,
    };
  });

  return c.json({
    success: true,
    roles: formattedRoles,
    permissions: allPermissions,
  });
});

// PATCH /api/v1/organizations/:orgId/roles/:roleId/permissions
// Update permissions for a role (Owner-only)
rolesRouter.patch(
  "/:orgId/roles/:roleId/permissions",
  requireTenant,
  requirePermission("organization.manage"),
  async (c) => {
    const roleId = c.req.param("roleId");
    const role = await db.select().from(schema.roles).where(eq(schema.roles.id, roleId)).get();

    if (!role) {
      throw new NotFoundError("Role not found");
    }

    if (role.name === "OWNER") {
      throw new ForbiddenError("Cannot modify OWNER role permissions; Owner retains full administrative access");
    }

    const body = await c.req.json();
    const validator = z.object({
      permissionIds: z.array(z.string()),
    });

    const { permissionIds } = validator.parse(body);

    // Delete existing role permissions
    await db.delete(schema.rolePermissions).where(eq(schema.rolePermissions.roleId, roleId)).run();

    // Re-insert selected permissions
    for (const permId of permissionIds) {
      const id = `${roleId}_${permId}`;
      await db.insert(schema.rolePermissions)
        .values({
          id,
          roleId,
          permissionId: permId,
        })
        .onConflictDoNothing()
        .run();
    }

    return c.json({
      success: true,
      message: `Permissions updated successfully for role ${role.name}`,
    });
  }
);
