import { sqliteTable, text, integer, index } from "drizzle-orm/sqlite-core";
import { organizations } from "./organizations.js";
import { users } from "./auth.js";

export const notifications = sqliteTable(
  "notifications",
  {
    id: text("id").primaryKey(),
    userId: text("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
    organizationId: text("organization_id").notNull().references(() => organizations.id, { onDelete: "cascade" }),
    title: text("title").notNull(),
    message: text("message").notNull(),
    type: text("type").notNull(), // payment.received, staff.invited, security.alert, etc.
    isRead: integer("is_read", { mode: "boolean" }).default(false).notNull(),
    metadataJson: text("metadata_json"),
    createdAt: integer("created_at", { mode: "timestamp" }).notNull(),
  },
  (table) => [
    index("notif_user_created_idx").on(table.userId, table.createdAt),
  ]
);

export const notificationPreferences = sqliteTable("notification_preferences", {
  id: text("id").primaryKey(),
  userId: text("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
  organizationId: text("organization_id").notNull().references(() => organizations.id, { onDelete: "cascade" }),
  paymentAlerts: integer("payment_alerts", { mode: "boolean" }).default(true).notNull(),
  paymentReceived: integer("payment_received", { mode: "boolean" }).default(true).notNull(),
  paymentSent: integer("payment_sent", { mode: "boolean" }).default(true).notNull(),
  paymentFailed: integer("payment_failed", { mode: "boolean" }).default(true).notNull(),
  paymentReversed: integer("payment_reversed", { mode: "boolean" }).default(true).notNull(),
  staffAlerts: integer("staff_alerts", { mode: "boolean" }).default(true).notNull(),
  staffActivity: integer("staff_activity", { mode: "boolean" }).default(true).notNull(),
  securityAlerts: integer("security_alerts", { mode: "boolean" }).default(true).notNull(),
  syncStatus: integer("sync_status", { mode: "boolean" }).default(false).notNull(),
  voiceAnnouncements: integer("voice_announcements", { mode: "boolean" }).default(false).notNull(),
  voiceEnabled: integer("voice_enabled", { mode: "boolean" }).default(false).notNull(),
  updatedAt: integer("updated_at", { mode: "timestamp" }).notNull(),
});
