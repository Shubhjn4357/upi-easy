import { sqliteTable, text, integer, real, index } from "drizzle-orm/sqlite-core";
import { organizations } from "./organizations.js";
import { bankAccounts, upiAccounts } from "./accounts.js";
import { users } from "./auth.js";

export const transactions = sqliteTable(
  "transactions",
  {
    id: text("id").primaryKey(),
    organizationId: text("organization_id").notNull().references(() => organizations.id, { onDelete: "cascade" }),
    bankAccountId: text("bank_account_id").references(() => bankAccounts.id, { onDelete: "set null" }),
    upiAccountId: text("upi_account_id").references(() => upiAccounts.id, { onDelete: "set null" }),
    type: text("type", {
      enum: ["PAYMENT", "REFUND", "REVERSAL", "TRANSFER", "COLLECTION", "ADJUSTMENT"],
    }).default("PAYMENT").notNull(),
    direction: text("direction", { enum: ["RECEIVED", "SENT"] }).default("RECEIVED").notNull(),
    amount: real("amount").notNull(),
    currency: text("currency").default("INR").notNull(),
    status: text("status", {
      enum: [
        "CREATED",
        "PENDING",
        "SUCCESS",
        "FAILED",
        "REVERSED",
        "REFUNDED",
        "UNKNOWN",
        "RECONCILIATION_REQUIRED",
      ],
    }).default("PENDING").notNull(),
    paymentMethod: text("payment_method").default("UPI").notNull(),
    provider: text("provider").default("NPCI").notNull(),
    providerTransactionId: text("provider_transaction_id"),
    upiTransactionId: text("upi_transaction_id"),
    referenceNumber: text("reference_number"), // RRN (Retrieval Reference Number)
    payerName: text("payer_name"),
    payerVpa: text("payer_vpa"),
    payeeName: text("payee_name").notNull(),
    payeeVpa: text("payee_vpa").notNull(),
    note: text("note"),
    invoiceId: text("invoice_id"),
    staffId: text("staff_id").references(() => users.id, { onDelete: "set null" }),
    source: text("source").default("UPI_INTENT").notNull(), // WEBHOOK, STATEMENT, UPI_INTENT, BANK_API
    occurredAt: integer("occurred_at", { mode: "timestamp" }).notNull(),
    createdAt: integer("created_at", { mode: "timestamp" }).notNull(),
    updatedAt: integer("updated_at", { mode: "timestamp" }).notNull(),
  },
  (table) => [
    index("txn_org_occurred_idx").on(table.organizationId, table.occurredAt),
    index("txn_org_status_idx").on(table.organizationId, table.status),
    index("txn_org_upi_idx").on(table.organizationId, table.upiAccountId),
    index("txn_provider_id_idx").on(table.providerTransactionId),
    index("txn_ref_num_idx").on(table.referenceNumber),
  ]
);

export const transactionEvents = sqliteTable("transaction_events", {
  id: text("id").primaryKey(),
  transactionId: text("transaction_id").notNull().references(() => transactions.id, { onDelete: "cascade" }),
  organizationId: text("organization_id").notNull().references(() => organizations.id, { onDelete: "cascade" }),
  eventType: text("event_type").notNull(), // e.g. transaction.created, transaction.reconciled
  previousStatus: text("previous_status"),
  newStatus: text("new_status").notNull(),
  payloadJson: text("payload_json"),
  createdAt: integer("created_at", { mode: "timestamp" }).notNull(),
});

export const transactionReferences = sqliteTable("transaction_references", {
  id: text("id").primaryKey(),
  transactionId: text("transaction_id").notNull().references(() => transactions.id, { onDelete: "cascade" }),
  referenceType: text("reference_type").notNull(), // RRN, BANK_REF, NPCI_TXN_ID
  referenceNumber: text("reference_number").notNull(),
  createdAt: integer("created_at", { mode: "timestamp" }).notNull(),
});
