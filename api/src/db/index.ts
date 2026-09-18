import Database from "better-sqlite3";
import { drizzle } from "drizzle-orm/better-sqlite3";
import * as schema from "./schema/index.js";
import { config } from "../config/index.js";
import { logger } from "../lib/logger.js";
import { eq } from "drizzle-orm";

const isPostgresUrl = config.DATABASE_URL.startsWith("postgres://") || config.DATABASE_URL.startsWith("postgresql://");
const sqlitePath = (process.env.NODE_ENV === "test" || isPostgresUrl)
  ? (process.env.NODE_ENV === "test" ? "./upieasy.test.db" : "./upieasy.db")
  : config.DATABASE_URL.replace("file:", "");

let sqlite: any;
let dbInstance: any;

try {
  sqlite = new Database(sqlitePath, { timeout: 15000 });
  sqlite.pragma("journal_mode = WAL");
  sqlite.pragma("foreign_keys = ON");
  sqlite.pragma("busy_timeout = 15000");
  dbInstance = drizzle(sqlite, { schema });
} catch (err: any) {
  console.warn("[UPI-Easy] SQLite/better-sqlite3 not available in this environment, using serverless fallback:", err?.message || err);
  dbInstance = new Proxy({}, {
    get(target, prop) {
      return (...args: any[]) => {
        throw new Error(`Database query (${String(prop)}) attempted but local SQLite native addon is not available in Cloudflare Workers isolate.`);
      };
    }
  });
}

export const db = dbInstance;

export function initDatabase() {
  if (!sqlite) {
    logger.warn("Skipping SQLite schema initialization (running in serverless isolate without local SQLite)");
    return;
  }
  // Ensure tables exist using raw DDL statements for zero-friction local/dev setup
  sqlite.exec(`
    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY,
      google_id TEXT UNIQUE,
      mobile_number TEXT,
      full_name TEXT,
      email TEXT,
      avatar_url TEXT,
      status TEXT NOT NULL DEFAULT 'ACTIVE',
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS devices (
      id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      device_id TEXT NOT NULL,
      device_model TEXT,
      os_version TEXT,
      fcm_token TEXT,
      is_active INTEGER NOT NULL DEFAULT 1,
      last_seen_at INTEGER NOT NULL,
      created_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS sessions (
      id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      device_id TEXT REFERENCES devices(id) ON DELETE SET NULL,
      refresh_token_hash TEXT,
      expires_at INTEGER NOT NULL,
      is_revoked INTEGER NOT NULL DEFAULT 0,
      ip_address TEXT,
      user_agent TEXT,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS otps (
      id TEXT PRIMARY KEY,
      mobile_number TEXT NOT NULL,
      otp_hash TEXT NOT NULL,
      attempts INTEGER NOT NULL DEFAULT 0,
      expires_at INTEGER NOT NULL,
      is_verified INTEGER NOT NULL DEFAULT 0,
      created_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS organizations (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      legal_business_name TEXT,
      category TEXT,
      pan_number TEXT,
      gstin TEXT,
      status TEXT NOT NULL DEFAULT 'ACTIVE',
      owner_id TEXT NOT NULL REFERENCES users(id),
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS roles (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      description TEXT,
      is_system INTEGER NOT NULL DEFAULT 0
    );

    CREATE TABLE IF NOT EXISTS permissions (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL UNIQUE,
      description TEXT,
      category TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS role_permissions (
      id TEXT PRIMARY KEY,
      role_id TEXT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
      permission_id TEXT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS organization_members (
      id TEXT PRIMARY KEY,
      organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      role_id TEXT NOT NULL REFERENCES roles(id),
      status TEXT NOT NULL DEFAULT 'ACTIVE',
      invited_by TEXT REFERENCES users(id),
      joined_at INTEGER,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL,
      UNIQUE(organization_id, user_id)
    );

    CREATE TABLE IF NOT EXISTS bank_accounts (
      id TEXT PRIMARY KEY,
      organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
      bank_name TEXT NOT NULL,
      account_holder_name TEXT NOT NULL,
      account_number_masked TEXT NOT NULL,
      ifsc_code TEXT NOT NULL,
      account_type TEXT NOT NULL DEFAULT 'CURRENT',
      status TEXT NOT NULL DEFAULT 'ACTIVE',
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS upi_accounts (
      id TEXT PRIMARY KEY,
      organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
      bank_account_id TEXT REFERENCES bank_accounts(id) ON DELETE SET NULL,
      vpa TEXT NOT NULL,
      payee_name TEXT NOT NULL,
      merchant_category_code TEXT NOT NULL DEFAULT '5411',
      is_default INTEGER NOT NULL DEFAULT 0,
      status TEXT NOT NULL DEFAULT 'ACTIVE',
      transaction_count INTEGER NOT NULL DEFAULT 0,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS qr_codes (
      id TEXT PRIMARY KEY,
      organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
      upi_account_id TEXT NOT NULL REFERENCES upi_accounts(id) ON DELETE CASCADE,
      title TEXT NOT NULL,
      qr_payload TEXT NOT NULL,
      type TEXT NOT NULL DEFAULT 'STATIC',
      amount REAL,
      note TEXT,
      usage_count INTEGER NOT NULL DEFAULT 0,
      is_active INTEGER NOT NULL DEFAULT 1,
      created_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS transactions (
      id TEXT PRIMARY KEY,
      organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
      bank_account_id TEXT REFERENCES bank_accounts(id) ON DELETE SET NULL,
      upi_account_id TEXT REFERENCES upi_accounts(id) ON DELETE SET NULL,
      type TEXT NOT NULL DEFAULT 'PAYMENT',
      direction TEXT NOT NULL DEFAULT 'RECEIVED',
      amount REAL NOT NULL,
      currency TEXT NOT NULL DEFAULT 'INR',
      status TEXT NOT NULL DEFAULT 'PENDING',
      payment_method TEXT NOT NULL DEFAULT 'UPI',
      provider TEXT NOT NULL DEFAULT 'NPCI',
      provider_transaction_id TEXT,
      upi_transaction_id TEXT,
      reference_number TEXT,
      payer_name TEXT,
      payer_vpa TEXT,
      payee_name TEXT NOT NULL,
      payee_vpa TEXT NOT NULL,
      note TEXT,
      invoice_id TEXT,
      staff_id TEXT REFERENCES users(id) ON DELETE SET NULL,
      source TEXT NOT NULL DEFAULT 'UPI_INTENT',
      occurred_at INTEGER NOT NULL,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS transaction_events (
      id TEXT PRIMARY KEY,
      transaction_id TEXT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
      organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
      event_type TEXT NOT NULL,
      previous_status TEXT,
      new_status TEXT NOT NULL,
      payload_json TEXT,
      created_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS transaction_references (
      id TEXT PRIMARY KEY,
      transaction_id TEXT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
      reference_type TEXT NOT NULL,
      reference_number TEXT NOT NULL,
      created_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS audit_logs (
      id TEXT PRIMARY KEY,
      organization_id TEXT REFERENCES organizations(id) ON DELETE CASCADE,
      actor_id TEXT REFERENCES users(id) ON DELETE SET NULL,
      action TEXT NOT NULL,
      resource_type TEXT NOT NULL,
      resource_id TEXT,
      metadata_json TEXT,
      ip_address TEXT,
      device_id TEXT,
      created_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS idempotency_keys (
      id TEXT PRIMARY KEY,
      idempotency_key TEXT NOT NULL UNIQUE,
      organization_id TEXT REFERENCES organizations(id) ON DELETE CASCADE,
      operation TEXT NOT NULL,
      request_hash TEXT NOT NULL,
      response_status INTEGER NOT NULL,
      response_body TEXT NOT NULL,
      created_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS outbox_events (
      sequence INTEGER PRIMARY KEY AUTOINCREMENT,
      id TEXT NOT NULL UNIQUE,
      organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
      event_type TEXT NOT NULL,
      payload_json TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'PENDING',
      created_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS sync_cursors (
      id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      device_id TEXT NOT NULL,
      organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
      last_sequence INTEGER NOT NULL DEFAULT 0,
      updated_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS notifications (
      id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
      title TEXT NOT NULL,
      message TEXT NOT NULL,
      type TEXT NOT NULL,
      is_read INTEGER NOT NULL DEFAULT 0,
      metadata_json TEXT,
      created_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS notification_preferences (
      id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
      payment_alerts INTEGER NOT NULL DEFAULT 1,
      staff_alerts INTEGER NOT NULL DEFAULT 1,
      security_alerts INTEGER NOT NULL DEFAULT 1,
      voice_announcements INTEGER NOT NULL DEFAULT 0,
      updated_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS reconciliation_records (
      id TEXT PRIMARY KEY,
      organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
      transaction_id TEXT REFERENCES transactions(id) ON DELETE SET NULL,
      provider_name TEXT NOT NULL,
      provider_transaction_id TEXT NOT NULL,
      reference_number TEXT,
      match_status TEXT NOT NULL,
      discrepancy_reason TEXT,
      reconciled_at INTEGER NOT NULL,
      created_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS payment_requests (
      id TEXT PRIMARY KEY,
      organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
      upi_account_id TEXT NOT NULL,
      amount REAL,
      payer_vpa TEXT,
      note TEXT,
      status TEXT NOT NULL DEFAULT 'PENDING',
      expires_at INTEGER NOT NULL,
      created_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS provider_connections (
      id TEXT PRIMARY KEY,
      organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
      provider_name TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'ACTIVE',
      webhook_secret_encrypted TEXT,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS provider_webhooks (
      id TEXT PRIMARY KEY,
      provider_name TEXT NOT NULL,
      event_id TEXT,
      signature TEXT,
      payload_json TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'RECEIVED',
      error_message TEXT,
      created_at INTEGER NOT NULL
    );
  `);

  // Migration safeguards for schema updates
  try { sqlite.exec("ALTER TABLE users ADD COLUMN google_id TEXT"); } catch {}
  try { sqlite.exec("ALTER TABLE users ADD COLUMN avatar_url TEXT"); } catch {}

  seedPermissionsAndRoles();
  logger.info("Database initialized with schema and seed data");
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
