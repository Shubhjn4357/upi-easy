import { sqliteTable, text, integer, real } from "drizzle-orm/sqlite-core";
import { organizations } from "./organizations.js";

export const bankAccounts = sqliteTable("bank_accounts", {
  id: text("id").primaryKey(),
  organizationId: text("organization_id").notNull().references(() => organizations.id, { onDelete: "cascade" }),
  bankName: text("bank_name").notNull(),
  accountHolderName: text("account_holder_name").notNull(),
  accountNumberMasked: text("account_number_masked").notNull(), // e.g. "••••1234"
  ifscCode: text("ifsc_code").notNull(),
  accountType: text("account_type", { enum: ["CURRENT", "SAVINGS", "OVERDRAFT"] }).default("CURRENT").notNull(),
  status: text("status", { enum: ["ACTIVE", "INACTIVE", "ARCHIVED"] }).default("ACTIVE").notNull(),
  createdAt: integer("created_at", { mode: "timestamp" }).notNull(),
  updatedAt: integer("updated_at", { mode: "timestamp" }).notNull(),
});

export const upiAccounts = sqliteTable("upi_accounts", {
  id: text("id").primaryKey(),
  organizationId: text("organization_id").notNull().references(() => organizations.id, { onDelete: "cascade" }),
  bankAccountId: text("bank_account_id").references(() => bankAccounts.id, { onDelete: "set null" }),
  vpa: text("vpa").notNull(), // e.g. "business@icici"
  payeeName: text("payee_name").notNull(),
  merchantCategoryCode: text("merchant_category_code").default("5411").notNull(),
  isDefault: integer("is_default", { mode: "boolean" }).default(false).notNull(),
  status: text("status", { enum: ["ACTIVE", "INACTIVE", "ARCHIVED"] }).default("ACTIVE").notNull(),
  transactionCount: integer("transaction_count").default(0).notNull(),
  createdAt: integer("created_at", { mode: "timestamp" }).notNull(),
  updatedAt: integer("updated_at", { mode: "timestamp" }).notNull(),
});

export const qrCodes = sqliteTable("qr_codes", {
  id: text("id").primaryKey(),
  organizationId: text("organization_id").notNull().references(() => organizations.id, { onDelete: "cascade" }),
  upiAccountId: text("upi_account_id").notNull().references(() => upiAccounts.id, { onDelete: "cascade" }),
  title: text("title").notNull(),
  qrPayload: text("qr_payload").notNull(), // Complete upi://pay?... string
  type: text("type", { enum: ["STATIC", "DYNAMIC"] }).default("STATIC").notNull(),
  amount: real("amount"),
  note: text("note"),
  usageCount: integer("usage_count").default(0).notNull(),
  isActive: integer("is_active", { mode: "boolean" }).default(true).notNull(),
  createdAt: integer("created_at", { mode: "timestamp" }).notNull(),
});
