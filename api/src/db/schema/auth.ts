import { sqliteTable, text, integer } from "drizzle-orm/sqlite-core";

export const users = sqliteTable("users", {
  id: text("id").primaryKey(),
  googleId: text("google_id").unique(),
  mobileNumber: text("mobile_number"),
  fullName: text("full_name"),
  email: text("email"),
  avatarUrl: text("avatar_url"),
  status: text("status", { enum: ["ACTIVE", "SUSPENDED", "PENDING"] }).default("ACTIVE").notNull(),
  createdAt: integer("created_at", { mode: "timestamp" }).notNull(),
  updatedAt: integer("updated_at", { mode: "timestamp" }).notNull(),
});

export const devices = sqliteTable("devices", {
  id: text("id").primaryKey(),
  userId: text("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
  deviceId: text("device_id").notNull(),
  deviceModel: text("device_model"),
  osVersion: text("os_version"),
  fcmToken: text("fcm_token"),
  isActive: integer("is_active", { mode: "boolean" }).default(true).notNull(),
  lastSeenAt: integer("last_seen_at", { mode: "timestamp" }).notNull(),
  createdAt: integer("created_at", { mode: "timestamp" }).notNull(),
});

export const sessions = sqliteTable("sessions", {
  id: text("id").primaryKey(),
  userId: text("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
  deviceId: text("device_id").references(() => devices.id, { onDelete: "set null" }),
  refreshTokenHash: text("refresh_token_hash"),
  expiresAt: integer("expires_at", { mode: "timestamp" }).notNull(),
  isRevoked: integer("is_revoked", { mode: "boolean" }).default(false).notNull(),
  ipAddress: text("ip_address"),
  userAgent: text("user_agent"),
  createdAt: integer("created_at", { mode: "timestamp" }).notNull(),
  updatedAt: integer("updated_at", { mode: "timestamp" }).notNull(),
});

export const otps = sqliteTable("otps", {
  id: text("id").primaryKey(),
  mobileNumber: text("mobile_number").notNull(),
  otpHash: text("otp_hash").notNull(),
  attempts: integer("attempts").default(0).notNull(),
  expiresAt: integer("expires_at", { mode: "timestamp" }).notNull(),
  isVerified: integer("is_verified", { mode: "boolean" }).default(false).notNull(),
  createdAt: integer("created_at", { mode: "timestamp" }).notNull(),
});
