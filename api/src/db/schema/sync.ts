import { sqliteTable, text, integer, index } from "drizzle-orm/sqlite-core";
import { organizations } from "./organizations.js";
import { users } from "./auth.js";

export const outboxEvents = sqliteTable(
  "outbox_events",
  {
    sequence: integer("sequence").primaryKey({ autoIncrement: true }),
    id: text("id").notNull().unique(),
    organizationId: text("organization_id").notNull().references(() => organizations.id, { onDelete: "cascade" }),
    eventType: text("event_type").notNull(), // transaction.created, upi.updated, etc.
    payloadJson: text("payload_json").notNull(),
    status: text("status", { enum: ["PENDING", "PROCESSED", "FAILED"] }).default("PENDING").notNull(),
    createdAt: integer("created_at", { mode: "timestamp" }).notNull(),
  },
  (table) => [
    index("outbox_org_seq_idx").on(table.organizationId, table.sequence),
  ]
);

export const syncCursors = sqliteTable("sync_cursors", {
  id: text("id").primaryKey(),
  userId: text("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
  deviceId: text("device_id").notNull(),
  organizationId: text("organization_id").notNull().references(() => organizations.id, { onDelete: "cascade" }),
  lastSequence: integer("last_sequence").default(0).notNull(),
  updatedAt: integer("updated_at", { mode: "timestamp" }).notNull(),
});
