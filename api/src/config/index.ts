import dotenv from "dotenv";
import { z } from "zod";

dotenv.config();

const envSchema = z.object({
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  PORT: z.coerce.number().default(8080),
  DATABASE_URL: z.string().default("./upieasy.db"),
  JWT_SECRET: z.string().min(16).default("super-secret-jwt-key-for-upi-easy-auth-2026"),
  JWT_EXPIRY: z.string().default("15m"),
  REFRESH_TOKEN_EXPIRY: z.string().default("30d"),
  WEBHOOK_SECRET: z.string().default("default-webhook-secret-key-for-local-testing"),
});

export const config = envSchema.parse(process.env);
