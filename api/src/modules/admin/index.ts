import { Hono } from "hono";
import { z } from "zod";
import { getRawDbClient, db } from "../../db/index.js";
import { requireAuth } from "../../middleware/auth.js";
import { AppError, NotFoundError } from "../../lib/errors.js";
import { seedDemoMerchantData } from "../../db/seed.js";
import { generateId } from "../../lib/crypto.js";
import type { AppEnv } from "../../types/hono.js";

export const adminRouter = new Hono<AppEnv>();

export const ALLOWED_TABLES = [
  "users",
  "organizations",
  "organization_members",
  "organization_invites",
  "roles",
  "permissions",
  "role_permissions",
  "upi_accounts",
  "bank_accounts",
  "qr_codes",
  "transactions",
  "transaction_events",
  "transaction_references",
  "audit_logs",
  "devices",
  "sessions",
  "payment_accounts",
  "observed_payment_events",
  "notification_preferences",
] as const;

type AllowedTable = typeof ALLOWED_TABLES[number];

function isAllowedTable(name: string): name is AllowedTable {
  return (ALLOWED_TABLES as readonly string[]).includes(name);
}

// 1. Telemetry & Comprehensive System Health
adminRouter.get("/health", async (c) => {
  const client = getRawDbClient(c.env);
  const tableCounts: Record<string, number> = {};

  if (client) {
    for (const table of ALLOWED_TABLES) {
      try {
        const row = await client.get<{ count: number }>(`SELECT count(*) as count FROM ${table}`);
        tableCounts[table] = row ? row.count : 0;
      } catch {
        tableCounts[table] = 0;
      }
    }
  }

  const mem = typeof process !== "undefined" && process.memoryUsage ? process.memoryUsage() : { rss: 0, heapTotal: 0, heapUsed: 0, external: 0 };
  const uptime = typeof process !== "undefined" && process.uptime ? Math.floor(process.uptime()) : 0;

  return c.json({
    status: "healthy",
    timestamp: new Date().toISOString(),
    uptimeSeconds: uptime,
    service: "upi-easy-api",
    version: "1.0.0",
    nodeVersion: typeof process !== "undefined" ? process.version : "worker",
    platform: typeof process !== "undefined" ? process.platform : "cloudflare",
    environment: ((c.env as any)?.NODE_ENV as string) || (typeof process !== "undefined" ? process.env.NODE_ENV : "production") || "production",
    memory: {
      rssMb: (mem.rss / 1024 / 1024).toFixed(1),
      heapTotalMb: (mem.heapTotal / 1024 / 1024).toFixed(1),
      heapUsedMb: (mem.heapUsed / 1024 / 1024).toFixed(1),
      externalMb: (mem.external / 1024 / 1024).toFixed(1),
    },
    tables: tableCounts,
  });
});

// 2. List all tables with schema details & counts
adminRouter.get("/tables", async (c) => {
  const client = getRawDbClient(c.env);
  if (!client) {
    return c.json({ success: true, tables: [] });
  }

  const tables = await Promise.all(
    ALLOWED_TABLES.map(async (tableName) => {
      try {
        const countRow = await client.get<{ count: number }>(`SELECT count(*) as count FROM ${tableName}`);
        const columns = await client.all<{
          cid: number;
          name: string;
          type: string;
          notnull: number;
          dflt_value: any;
          pk: number;
        }>(`PRAGMA table_info(${tableName})`);

        return {
          name: tableName,
          rowCount: countRow ? countRow.count : 0,
          columns: columns.map((col) => ({
            name: col.name,
            type: col.type,
            isPrimary: col.pk === 1,
            isNullable: col.notnull === 0,
          })),
        };
      } catch {
        return {
          name: tableName,
          rowCount: 0,
          columns: [],
        };
      }
    })
  );

  return c.json({ success: true, tables });
});

// 3. Query paginated table data
adminRouter.get("/tables/:table", async (c) => {
  const tableName = c.req.param("table");
  if (!isAllowedTable(tableName)) {
    throw new AppError(`Table '${tableName}' is not accessible or invalid`, 400);
  }

  const client = getRawDbClient(c.env);
  if (!client) {
    throw new AppError("Database instance unavailable", 500);
  }

  const limit = Math.min(Math.max(Number(c.req.query("limit")) || 25, 1), 100);
  const offset = Math.max(Number(c.req.query("offset")) || 0, 0);
  const search = c.req.query("search")?.trim();

  const columns = await client.all<{
    name: string;
    type: string;
    pk: number;
  }>(`PRAGMA table_info(${tableName})`);

  let countQuery = `SELECT count(*) as count FROM ${tableName}`;
  let dataQuery = `SELECT * FROM ${tableName}`;
  const params: any[] = [];

  if (search) {
    // Search text-compatible columns
    const searchableCols = columns
      .filter((col) => ["TEXT", "VARCHAR"].some((t) => col.type.toUpperCase().includes(t)))
      .map((col) => `${col.name} LIKE ?`);

    if (searchableCols.length > 0) {
      const whereClause = ` WHERE ${searchableCols.join(" OR ")}`;
      countQuery += whereClause;
      dataQuery += whereClause;
      searchableCols.forEach(() => params.push(`%${search}%`));
    }
  }

  const countRow = await client.get<{ count: number }>(countQuery, params);
  const total = countRow ? countRow.count : 0;

  // Order by primary key desc or created_at desc if exists
  const hasCreatedAt = columns.some((c) => c.name === "created_at");
  const pkCol = columns.find((c) => c.pk === 1)?.name || "rowid";
  const orderCol = hasCreatedAt ? "created_at" : pkCol;

  dataQuery += ` ORDER BY ${orderCol} DESC LIMIT ? OFFSET ?`;
  const rows = await client.all(dataQuery, [...params, limit, offset]);

  return c.json({
    success: true,
    table: tableName,
    total,
    limit,
    offset,
    columns: columns.map((col) => ({
      name: col.name,
      type: col.type,
      isPrimary: col.pk === 1,
    })),
    rows,
  });
});

// 4. Update row in table
adminRouter.patch("/tables/:table/:id", async (c) => {
  const tableName = c.req.param("table");
  const id = c.req.param("id");
  if (!isAllowedTable(tableName)) {
    throw new AppError(`Table '${tableName}' is not accessible or invalid`, 400);
  }

  const client = getRawDbClient(c.env);
  if (!client) throw new AppError("Database instance unavailable", 500);

  const body = await c.req.json();
  const columns = await client.all<{
    name: string;
    pk: number;
  }>(`PRAGMA table_info(${tableName})`);

  const validColumnNames = new Set(columns.map((c) => c.name));
  const pkCol = columns.find((c) => c.pk === 1)?.name || "id";

  const updates: string[] = [];
  const values: any[] = [];

  for (const [key, val] of Object.entries(body)) {
    if (key !== pkCol && validColumnNames.has(key)) {
      updates.push(`${key} = ?`);
      values.push(val);
    }
  }

  if (updates.length === 0) {
    throw new AppError("No valid fields provided for update", 400);
  }

  // Update timestamp if column exists
  if (validColumnNames.has("updated_at") && !body.updated_at) {
    updates.push("updated_at = ?");
    values.push(Date.now());
  }

  values.push(id);
  const result = await client.run(`UPDATE ${tableName} SET ${updates.join(", ")} WHERE ${pkCol} = ?`, values);

  if (result.changes === 0) {
    throw new NotFoundError(`Record with ${pkCol}='${id}' not found in ${tableName}`);
  }

  const updatedRow = await client.get(`SELECT * FROM ${tableName} WHERE ${pkCol} = ?`, [id]);
  return c.json({ success: true, message: "Record updated successfully", row: updatedRow });
});

// 5. Delete row in table
adminRouter.delete("/tables/:table/:id", async (c) => {
  const tableName = c.req.param("table");
  const id = c.req.param("id");
  if (!isAllowedTable(tableName)) {
    throw new AppError(`Table '${tableName}' is not accessible or invalid`, 400);
  }

  const client = getRawDbClient(c.env);
  if (!client) throw new AppError("Database instance unavailable", 500);

  const columns = await client.all<{
    name: string;
    pk: number;
  }>(`PRAGMA table_info(${tableName})`);
  const pkCol = columns.find((c) => c.pk === 1)?.name || "id";

  const result = await client.run(`DELETE FROM ${tableName} WHERE ${pkCol} = ?`, [id]);
  if (result.changes === 0) {
    throw new NotFoundError(`Record with ${pkCol}='${id}' not found in ${tableName}`);
  }

  return c.json({ success: true, message: `Record deleted successfully from ${tableName}` });
});

// 6. Insert row into table
adminRouter.post("/tables/:table", async (c) => {
  const tableName = c.req.param("table");
  if (!isAllowedTable(tableName)) {
    throw new AppError(`Table '${tableName}' is not accessible or invalid`, 400);
  }

  const client = getRawDbClient(c.env);
  if (!client) throw new AppError("Database instance unavailable", 500);

  const body = await c.req.json();
  const columns = await client.all<{
    name: string;
    pk: number;
    notnull: number;
  }>(`PRAGMA table_info(${tableName})`);

  const validColumnNames = new Set(columns.map((c) => c.name));
  const pkCol = columns.find((c) => c.pk === 1)?.name || "id";

  // Auto-generate ID if missing
  if (!body[pkCol] && validColumnNames.has(pkCol)) {
    body[pkCol] = generateId(tableName.slice(0, 3));
  }

  const now = Date.now();
  if (validColumnNames.has("created_at") && !body.created_at) {
    body.created_at = now;
  }
  if (validColumnNames.has("updated_at") && !body.updated_at) {
    body.updated_at = now;
  }

  const insertCols: string[] = [];
  const insertVals: any[] = [];
  const placeholders: string[] = [];

  for (const [key, val] of Object.entries(body)) {
    if (validColumnNames.has(key)) {
      insertCols.push(key);
      insertVals.push(val);
      placeholders.push("?");
    }
  }

  if (insertCols.length === 0) {
    throw new AppError("No valid columns provided for insert", 400);
  }

  await client.run(
    `INSERT INTO ${tableName} (${insertCols.join(", ")}) VALUES (${placeholders.join(", ")})`,
    insertVals
  );

  const insertedRow = await client.get(`SELECT * FROM ${tableName} WHERE ${pkCol} = ?`, [body[pkCol]]);
  return c.json({ success: true, message: "Record inserted successfully", row: insertedRow }, 201);
});

// 7. Re-seed demo dataset
adminRouter.post("/seed", async (c) => {
  seedDemoMerchantData();
  return c.json({ success: true, message: "Demo dataset seeded and verified successfully" });
});
