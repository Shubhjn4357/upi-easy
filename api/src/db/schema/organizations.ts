import { sqliteTable, text, integer, uniqueIndex } from "drizzle-orm/sqlite-core";
import { users } from "./auth.js";

export const organizations = sqliteTable("organizations", {
  id: text("id").primaryKey(),
  name: text("name").notNull(),
  legalBusinessName: text("legal_business_name"),
  category: text("category"), // e.g. RETAIL, FOOD, SERVICES, TECH
  panNumber: text("pan_number"),
  gstin: text("gstin"),
  status: text("status", { enum: ["ACTIVE", "SUSPENDED", "INACTIVE"] }).default("ACTIVE").notNull(),
  ownerId: text("owner_id").notNull().references(() => users.id),
  createdAt: integer("created_at", { mode: "timestamp" }).notNull(),
  updatedAt: integer("updated_at", { mode: "timestamp" }).notNull(),
});

export const roles = sqliteTable("roles", {
  id: text("id").primaryKey(),
  name: text("name").notNull(), // OWNER, MANAGER, CASHIER, ACCOUNTANT
  description: text("description"),
  isSystem: integer("is_system", { mode: "boolean" }).default(false).notNull(),
});

export const permissions = sqliteTable("permissions", {
  id: text("id").primaryKey(),
  name: text("name").notNull().unique(), // e.g. transactions.read, accounts.manage
  description: text("description"),
  category: text("category").notNull(),
});

export const rolePermissions = sqliteTable("role_permissions", {
  id: text("id").primaryKey(),
  roleId: text("role_id").notNull().references(() => roles.id, { onDelete: "cascade" }),
  permissionId: text("permission_id").notNull().references(() => permissions.id, { onDelete: "cascade" }),
});

export const organizationMembers = sqliteTable(
  "organization_members",
  {
    id: text("id").primaryKey(),
    organizationId: text("organization_id").notNull().references(() => organizations.id, { onDelete: "cascade" }),
    userId: text("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
    roleId: text("role_id").notNull().references(() => roles.id),
    status: text("status", { enum: ["ACTIVE", "INVITED", "SUSPENDED"] }).default("ACTIVE").notNull(),
    invitedBy: text("invited_by").references(() => users.id),
    joinedAt: integer("joined_at", { mode: "timestamp" }),
    createdAt: integer("created_at", { mode: "timestamp" }).notNull(),
    updatedAt: integer("updated_at", { mode: "timestamp" }).notNull(),
  },
  (table) => [
    uniqueIndex("org_member_unique").on(table.organizationId, table.userId),
  ]
);
