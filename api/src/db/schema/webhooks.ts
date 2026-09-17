import { sqliteTable, text, integer } from "drizzle-orm/sqlite-core";
import { organizations } from "./organizations.js";

export const providerConnections = sqliteTable("provider_connections", {
  id: text("id").primaryKey(),
  organizationId: text("organization_id").notNull().references(() => organizations.id, { onDelete: "cascade" }),
  providerName: text("provider_name").notNull(), // e.g. HDFC_SMART_HUB, ICICI_EAZYPAY, CASHFREE_UPI, RAZORPAY_UPI
  status: text("status", { enum: ["ACTIVE", "INACTIVE", "ERROR"] }).default("ACTIVE").notNull(),
  webhookSecretEncrypted: text("webhook_secret_encrypted"),
  createdAt: integer("created_at", { mode: "timestamp" }).notNull(),
  updatedAt: integer("updated_at", { mode: "timestamp" }).notNull(),
});

export const providerWebhooks = sqliteTable("provider_webhooks", {
  id: text("id").primaryKey(),
  providerName: text("provider_name").notNull(),
  eventId: text("event_id"),
  signature: text("signature"),
  payloadJson: text("payload_json").notNull(),
  status: text("status", { enum: ["RECEIVED", "PROCESSED", "IGNORED", "FAILED"] }).default("RECEIVED").notNull(),
  errorMessage: text("error_message"),
  createdAt: integer("created_at", { mode: "timestamp" }).notNull(),
});
