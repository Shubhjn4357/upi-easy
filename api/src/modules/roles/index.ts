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
  let allRoles = await db.select().from(schema.roles).all();
  let allPermissions = await db.select().from(schema.permissions).all();
  let allRolePerms = await db.select().from(schema.rolePermissions).all();

  if (allRoles.length === 0) {
    const standardRoles = [
      { id: "role_owner", name: "OWNER", description: "Full business control", isSystem: true },
      { id: "role_manager", name: "MANAGER", description: "Business and staff operations", isSystem: true },
      { id: "role_cashier", name: "CASHIER", description: "Payment initiation and transaction records", isSystem: true },
      { id: "role_accountant", name: "ACCOUNTANT", description: "Reconciliation, reporting and exports", isSystem: true },
    ];
    for (const r of standardRoles) {
      try { await db.insert(schema.roles).values(r).onConflictDoNothing().run(); } catch (_) {}
    }
    allRoles = await db.select().from(schema.roles).all();
  }

  if (allPermissions.length === 0) {
    const standardPermissions = [
      { id: "perm_tx_read", name: "transactions.read", description: "View payment transactions & settlement history", category: "transactions" },
      { id: "perm_tx_export", name: "transactions.export", description: "Export reports to Excel & CSV spreadsheets", category: "transactions" },
      { id: "perm_tx_create", name: "transactions.create", description: "Record manual payments & initiate transactions", category: "transactions" },
      { id: "perm_tx_refund", name: "transactions.refund", description: "Process payment refunds back to customers", category: "transactions" },
      { id: "perm_tx_delete", name: "transactions.delete", description: "Delete transactions", category: "transactions" },
      { id: "perm_evt_ingest", name: "payment_events.ingest", description: "Auto-detect and capture payment notifications & SMS", category: "transactions" },
      { id: "perm_acc_read", name: "accounts.read", description: "View settlement bank accounts", category: "accounts" },
      { id: "perm_acc_manage", name: "accounts.manage", description: "Add, update, or remove linked bank accounts", category: "accounts" },
      { id: "perm_upi_read", name: "upi.read", description: "View active UPI IDs and VPAs", category: "upi" },
      { id: "perm_upi_manage", name: "upi.manage", description: "Configure and manage business UPI handles", category: "upi" },
      { id: "perm_qr_create", name: "qr.create", description: "Generate custom counter and customer QR codes", category: "qr" },
      { id: "perm_staff_read", name: "staff.read", description: "View team members and staff list", category: "staff" },
      { id: "perm_staff_manage", name: "staff.manage", description: "Invite staff, assign roles, or remove members", category: "staff" },
      { id: "perm_rep_read", name: "reports.read", description: "Access sales reports and business analytics", category: "reports" },
      { id: "perm_org_manage", name: "organization.manage", description: "Manage organization settings and business profile", category: "organization" },
    ];
    for (const p of standardPermissions) {
      try { await db.insert(schema.permissions).values(p).onConflictDoNothing().run(); } catch (_) {}
    }
    allPermissions = await db.select().from(schema.permissions).all();
  }

  if (allRolePerms.length === 0) {
    const rolePermMap: Record<string, string[]> = {
      role_owner: allPermissions.map((p) => p.id),
      role_manager: [
        "perm_tx_read",
        "perm_tx_export",
        "perm_tx_create",
        "perm_tx_refund",
        "perm_tx_delete",
        "perm_evt_ingest",
        "perm_acc_read",
        "perm_upi_read",
        "perm_qr_create",
        "perm_staff_read",
        "perm_staff_manage",
        "perm_rep_read",
      ],
      role_cashier: ["perm_tx_read", "perm_tx_create", "perm_evt_ingest", "perm_upi_read", "perm_qr_create"],
      role_accountant: ["perm_tx_read", "perm_tx_export", "perm_rep_read", "perm_acc_read", "perm_upi_read"],
    };
    for (const [roleId, permIds] of Object.entries(rolePermMap)) {
      for (const permId of permIds) {
        try {
          await db.insert(schema.rolePermissions)
            .values({ id: `${roleId}_${permId}`, roleId, permissionId: permId })
            .onConflictDoNothing()
            .run();
        } catch (_) {}
      }
    }
    allRolePerms = await db.select().from(schema.rolePermissions).all();
  }


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
