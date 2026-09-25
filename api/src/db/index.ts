import Database from "better-sqlite3";
import { drizzle as drizzleBetterSqlite3 } from "drizzle-orm/better-sqlite3";
import { migrate } from "drizzle-orm/better-sqlite3/migrator";
import { drizzle as drizzleD1 } from "drizzle-orm/d1";
import * as schema from "./schema/index.js";
import { config } from "../config/index.js";
import { logger } from "../lib/logger.js";
import { eq } from "drizzle-orm";
import { seedDemoMerchantData } from "./seed.js";

const isPostgresUrl = config.DATABASE_URL.startsWith("postgresql:") || config.DATABASE_URL.startsWith("postgres:");
const sqlitePath = process.env.NODE_ENV === "test"
  ? "./upieasy.test.db"
  : isPostgresUrl || !config.DATABASE_URL
    ? "./upieasy.db"
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

let rawD1: any = null;

export function setD1Database(d1Database: any) {
  if (d1Database) {
    rawD1 = d1Database;
    dbInstance = drizzleD1(d1Database, { schema });
  }
}

export function getRawD1(): any {
  return rawD1;
}

export function getRawDb(): any {
  return sqlite;
}

export interface RawDbClient {
  all<T = any>(query: string, params?: any[]): Promise<T[]>;
  get<T = any>(query: string, params?: any[]): Promise<T | null>;
  run(query: string, params?: any[]): Promise<{ changes: number }>;
}

export function getRawDbClient(env?: any): RawDbClient | null {
  const d1 = env?.upi_easy_db || env?.DB || rawD1;
  if (d1) {
    return {
      async all<T = any>(query: string, params: any[] = []): Promise<T[]> {
        const stmt = params.length > 0 ? d1.prepare(query).bind(...params) : d1.prepare(query);
        const res = await stmt.all();
        return (res.results || []) as T[];
      },
      async get<T = any>(query: string, params: any[] = []): Promise<T | null> {
        const stmt = params.length > 0 ? d1.prepare(query).bind(...params) : d1.prepare(query);
        const res = await stmt.first();
        return (res ?? null) as T | null;
      },
      async run(query: string, params: any[] = []): Promise<{ changes: number }> {
        const stmt = params.length > 0 ? d1.prepare(query).bind(...params) : d1.prepare(query);
        const res = await stmt.run();
        return { changes: res.meta?.changes ?? (res.success ? 1 : 0) };
      },
    };
  }

  if (sqlite) {
    return {
      async all<T = any>(query: string, params: any[] = []): Promise<T[]> {
        return sqlite.prepare(query).all(...params) as T[];
      },
      async get<T = any>(query: string, params: any[] = []): Promise<T | null> {
        return (sqlite.prepare(query).get(...params) ?? null) as T | null;
      },
      async run(query: string, params: any[] = []): Promise<{ changes: number }> {
        const res = sqlite.prepare(query).run(...params);
        return { changes: res.changes };
      },
    };
  }

  return null;
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

  // Ensure organization_invites table exists
  try {
    sqlite.exec(`
      CREATE TABLE IF NOT EXISTS organization_invites (
        id text PRIMARY KEY NOT NULL,
        organization_id text NOT NULL REFERENCES organizations(id) ON DELETE cascade,
        invited_user_id text REFERENCES users(id) ON DELETE cascade,
        invited_mobile text NOT NULL,
        invited_email text,
        invited_name text,
        role text NOT NULL,
        invited_by text NOT NULL REFERENCES users(id),
        status text DEFAULT 'PENDING' NOT NULL,
        expires_at integer NOT NULL,
        accepted_at integer,
        rejected_at integer,
        cancelled_at integer,
        created_at integer NOT NULL,
        updated_at integer NOT NULL
      );
    `);
  } catch (_) {}

  // Ensure organization_invites token column exists
  try { sqlite.exec(`ALTER TABLE organization_invites ADD COLUMN token text;`); } catch (_) {}
  try { sqlite.exec(`CREATE UNIQUE INDEX IF NOT EXISTS org_invites_token_idx ON organization_invites (token);`); } catch (_) {}

  // Ensure updated devices columns exist
  try { sqlite.exec(`ALTER TABLE devices ADD COLUMN platform text DEFAULT 'ANDROID';`); } catch (_) {}
  try { sqlite.exec(`ALTER TABLE devices ADD COLUMN app_version text;`); } catch (_) {}
  try { sqlite.exec(`ALTER TABLE devices ADD COLUMN updated_at integer;`); } catch (_) {}

  // Ensure is_default column on bank_accounts
  try { sqlite.exec(`ALTER TABLE bank_accounts ADD COLUMN is_default integer DEFAULT 0 NOT NULL;`); } catch (_) {}

  // Ensure updated notification_preferences columns exist
  try { sqlite.exec(`ALTER TABLE notification_preferences ADD COLUMN payment_received integer DEFAULT 1 NOT NULL;`); } catch (_) {}
  try { sqlite.exec(`ALTER TABLE notification_preferences ADD COLUMN payment_sent integer DEFAULT 1 NOT NULL;`); } catch (_) {}
  try { sqlite.exec(`ALTER TABLE notification_preferences ADD COLUMN payment_failed integer DEFAULT 1 NOT NULL;`); } catch (_) {}
  try { sqlite.exec(`ALTER TABLE notification_preferences ADD COLUMN payment_reversed integer DEFAULT 1 NOT NULL;`); } catch (_) {}
  try { sqlite.exec(`ALTER TABLE notification_preferences ADD COLUMN staff_activity integer DEFAULT 1 NOT NULL;`); } catch (_) {}
  try { sqlite.exec(`ALTER TABLE notification_preferences ADD COLUMN sync_status integer DEFAULT 0 NOT NULL;`); } catch (_) {}
  try { sqlite.exec(`ALTER TABLE notification_preferences ADD COLUMN voice_enabled integer DEFAULT 0 NOT NULL;`); } catch (_) {}

  // Ensure payment_accounts and observed_payment_events tables exist
  try {
    sqlite.exec(`
      CREATE TABLE IF NOT EXISTS payment_accounts (
        id text PRIMARY KEY NOT NULL,
        organization_id text NOT NULL REFERENCES organizations(id) ON DELETE cascade,
        label text NOT NULL,
        upi_id text NOT NULL,
        payment_app_id text NOT NULL,
        payment_app_package text NOT NULL,
        status text DEFAULT 'ACTIVE' NOT NULL,
        detection_enabled integer DEFAULT 1 NOT NULL,
        notification_access_required integer DEFAULT 1 NOT NULL,
        last_notification_detected_at integer,
        created_at integer NOT NULL,
        updated_at integer NOT NULL
      );
    `);
  } catch (_) {}

  try {
    sqlite.exec(`
      CREATE TABLE IF NOT EXISTS observed_payment_events (
        id text PRIMARY KEY NOT NULL,
        organization_id text NOT NULL REFERENCES organizations(id) ON DELETE cascade,
        payment_account_id text REFERENCES payment_accounts(id) ON DELETE set null,
        qr_id text REFERENCES qr_codes(id) ON DELETE set null,
        source_type text NOT NULL,
        source_package text NOT NULL,
        amount_minor integer,
        currency text DEFAULT 'INR' NOT NULL,
        direction text DEFAULT 'RECEIVED' NOT NULL,
        payer_name text,
        payer_vpa text,
        reference text,
        event_fingerprint text NOT NULL,
        match_status text DEFAULT 'MATCHED' NOT NULL,
        verification_status text DEFAULT 'OBSERVED' NOT NULL,
        observed_at integer NOT NULL,
        created_at integer NOT NULL
      );
      CREATE INDEX IF NOT EXISTS observed_evt_org_fp_idx ON observed_payment_events (organization_id, event_fingerprint);
      CREATE INDEX IF NOT EXISTS observed_evt_org_time_idx ON observed_payment_events (organization_id, observed_at);
    `);
  } catch (_) {}

  // Ensure updated qr_codes columns exist
  try { sqlite.exec(`ALTER TABLE qr_codes ADD COLUMN payment_account_id text REFERENCES payment_accounts(id) ON DELETE set null;`); } catch (_) {}

  // Ensure updated transactions columns exist
  try { sqlite.exec(`ALTER TABLE transactions ADD COLUMN payment_account_id text REFERENCES payment_accounts(id) ON DELETE set null;`); } catch (_) {}
  try { sqlite.exec(`ALTER TABLE transactions ADD COLUMN verification_status text DEFAULT 'UNVERIFIED' NOT NULL;`); } catch (_) {}
  try { sqlite.exec(`ALTER TABLE transactions ADD COLUMN event_source text DEFAULT 'UPI_INTENT' NOT NULL;`); } catch (_) {}

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
    { id: "perm_evt_ingest", name: "payment_events.ingest", description: "Ingest observed payment events", category: "transactions" },
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
      const id = `${roleId}_${permId}`;
      db.insert(schema.rolePermissions)
        .values({ id, roleId, permissionId: permId })
        .onConflictDoNothing()
        .run();
    }
  }
}
