import Database from "better-sqlite3";
import { drizzle as drizzleBetterSqlite3 } from "drizzle-orm/better-sqlite3";
import { migrate } from "drizzle-orm/better-sqlite3/migrator";
import { drizzle as drizzleD1 } from "drizzle-orm/d1";
import * as schema from "./schema/index.js";
import { config } from "../config/index.js";
import { logger } from "../lib/logger.js";
import { eq } from "drizzle-orm";
import { seedDemoMerchantData } from "./seed.js";

const sqlitePath = process.env.NODE_ENV === "test"
  ? "./upieasy.test.db"
  : config.DATABASE_URL.replace("file:", "");

let sqlite: any;
let dbInstance: any;

try {
  sqlite = new Database(sqlitePath, { timeout: 15000 });
  sqlite.pragma("journal_mode = WAL");
  sqlite.pragma("foreign_keys = ON");
  sqlite.pragma("busy_timeout = 15000");
  dbInstance = drizzleBetterSqlite3(sqlite, { schema });
} catch (err: any) {
  // Better-sqlite3 native addon not available in Cloudflare Workers isolate
}

export function setD1Database(d1Database: any) {
  if (d1Database) {
    dbInstance = drizzleD1(d1Database, { schema });
  }
}

export const db = new Proxy({} as any, {
  get(target, prop) {
    if (!dbInstance) {
      throw new Error(
        `Database query attempted but neither local SQLite nor Cloudflare D1 binding (upi_easy_db / DB) is initialized.`
      );
    }
    const val = dbInstance[prop];
    if (typeof val === "function") {
      return val.bind(dbInstance);
    }
    return val;
  },
});

export function initDatabase() {
  if (!sqlite) {
    logger.warn("Skipping SQLite schema initialization (running in serverless isolate without local SQLite)");
    return;
  }

  // Execute migrations generated from Drizzle schema
  try {
    migrate(dbInstance, { migrationsFolder: "./drizzle" });
  } catch (err: any) {
    // If tables already exist or already migrated
    logger.debug("Drizzle migration notice: " + (err?.message || err));
  }

  seedPermissionsAndRoles();
  seedDemoMerchantData(db);
  logger.info("Database initialized with Drizzle schemas and seed data");
}

function seedPermissionsAndRoles() {
  const standardPermissions = [
    { id: "perm_tx_read", name: "transactions.read", description: "View transactions", category: "transactions" },
    { id: "perm_tx_export", name: "transactions.export", description: "Export transactions", category: "transactions" },
    { id: "perm_tx_create", name: "transactions.create", description: "Create transactions", category: "transactions" },
    { id: "perm_tx_refund", name: "transactions.refund", description: "Initiate refunds", category: "transactions" },
    { id: "perm_acc_read", name: "accounts.read", description: "View bank accounts", category: "accounts" },
    { id: "perm_acc_manage", name: "accounts.manage", description: "Manage bank accounts", category: "accounts" },
    { id: "perm_upi_read", name: "upi.read", description: "View UPI IDs", category: "upi" },
    { id: "perm_upi_manage", name: "upi.manage", description: "Manage UPI IDs", category: "upi" },
    { id: "perm_qr_create", name: "qr.create", description: "Generate QR codes", category: "qr" },
    { id: "perm_staff_read", name: "staff.read", description: "View staff members", category: "staff" },
    { id: "perm_staff_manage", name: "staff.manage", description: "Manage staff members", category: "staff" },
    { id: "perm_rep_read", name: "reports.read", description: "View reports", category: "reports" },
    { id: "perm_org_manage", name: "organization.manage", description: "Manage organization settings", category: "organization" },
  ];

  for (const perm of standardPermissions) {
    db.insert(schema.permissions).values(perm).onConflictDoNothing().run();
  }

  const standardRoles = [
    { id: "role_owner", name: "OWNER", description: "Full business control", isSystem: true },
    { id: "role_manager", name: "MANAGER", description: "Business and staff operations", isSystem: true },
    { id: "role_cashier", name: "CASHIER", description: "Payment initiation and transaction records", isSystem: true },
    { id: "role_accountant", name: "ACCOUNTANT", description: "Reconciliation, reporting and exports", isSystem: true },
  ];

  for (const r of standardRoles) {
    db.insert(schema.roles).values(r).onConflictDoNothing().run();
  }

  // Bind role permissions
  const rolePermMap: Record<string, string[]> = {
    role_owner: standardPermissions.map((p) => p.id),
    role_manager: [
      "perm_tx_read",
      "perm_tx_export",
      "perm_tx_create",
      "perm_acc_read",
      "perm_upi_read",
      "perm_upi_manage",
      "perm_qr_create",
      "perm_staff_read",
      "perm_staff_manage",
      "perm_rep_read",
    ],
    role_cashier: ["perm_tx_read", "perm_tx_create", "perm_upi_read", "perm_qr_create"],
    role_accountant: ["perm_tx_read", "perm_tx_export", "perm_rep_read", "perm_acc_read", "perm_upi_read"],
  };

  for (const [roleId, permIds] of Object.entries(rolePermMap)) {
    for (const permId of permIds) {
      const id = `${roleId}_${permId}`;
      db.insert(schema.rolePermissions)
        .values({ id, roleId, permissionId: permId })
        .onConflictDoNothing()
        .run();
    }
  }
}
