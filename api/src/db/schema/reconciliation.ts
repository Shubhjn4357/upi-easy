import { sqliteTable, text, integer, real } from "drizzle-orm/sqlite-core";
import { organizations } from "./organizations.js";
import { transactions } from "./transactions.js";

export const reconciliationRecords = sqliteTable("reconciliation_records", {
  id: text("id").primaryKey(),
  organizationId: text("organization_id").notNull().references(() => organizations.id, { onDelete: "cascade" }),
  transactionId: text("transaction_id").references(() => transactions.id, { onDelete: "set null" }),
  providerName: text("provider_name").notNull(),
  providerTransactionId: text("provider_transaction_id").notNull(),
  referenceNumber: text("reference_number"),
  matchStatus: text("match_status", {
    enum: ["MATCHED", "UNMATCHED", "DISCREPANCY", "MANUAL_RECONCILED"],
  }).notNull(),
  discrepancyReason: text("discrepancy_reason"),
  reconciledAt: integer("reconciled_at", { mode: "timestamp" }).notNull(),
  createdAt: integer("created_at", { mode: "timestamp" }).notNull(),
});

export const paymentRequests = sqliteTable("payment_requests", {
  id: text("id").primaryKey(),
  organizationId: text("organization_id").notNull().references(() => organizations.id, { onDelete: "cascade" }),
  upiAccountId: text("upi_account_id").notNull(),
  amount: real("amount"),
  payerVpa: text("payer_vpa"),
  note: text("note"),
  status: text("status", { enum: ["PENDING", "COMPLETED", "EXPIRED", "CANCELLED"] }).default("PENDING").notNull(),
  expiresAt: integer("expires_at", { mode: "timestamp" }).notNull(),
  createdAt: integer("created_at", { mode: "timestamp" }).notNull(),
});
