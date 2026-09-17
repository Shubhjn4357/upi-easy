import { Hono } from "hono";
import { cors } from "hono/cors";
import { secureHeaders } from "hono/secure-headers";
import { requestLogger } from "./middleware/logger.js";
import { errorHandler } from "./middleware/errorHandler.js";

import { authRouter } from "./modules/auth/index.js";
import { usersRouter } from "./modules/users/index.js";
import { organizationsRouter } from "./modules/organizations/index.js";
import { membersRouter } from "./modules/members/index.js";
import { accountsRouter } from "./modules/accounts/index.js";
import { upiRouter } from "./modules/upi/index.js";
import { qrRouter } from "./modules/qr/index.js";
import { transactionsRouter } from "./modules/transactions/index.js";
import { syncRouter } from "./modules/sync/index.js";
import { auditRouter } from "./modules/audit/index.js";
import { notificationsRouter } from "./modules/notifications/index.js";
import { webhooksRouter } from "./modules/webhooks/index.js";
import type { AppEnv } from "./types/hono.js";

export const app = new Hono<AppEnv>();

// Global Middlewares
app.use("*", secureHeaders());
app.use(
  "*",
  cors({
    origin: "*",
    allowHeaders: ["Content-Type", "Authorization", "X-Organization-Id", "X-Device-Id", "Idempotency-Key"],
    allowMethods: ["GET", "POST", "PATCH", "DELETE", "OPTIONS"],
  })
);
app.use("*", requestLogger);
app.onError(errorHandler);

// Health check
app.get("/health", (c) => {
  return c.json({
    status: "healthy",
    timestamp: new Date().toISOString(),
    service: "upi-easy-api",
    version: "1.0.0",
  });
});

// Mount /api/v1 endpoints
const v1 = new Hono<AppEnv>();

v1.route("/auth", authRouter);
v1.route("/me", usersRouter);
v1.route("/organizations", organizationsRouter);
v1.route("/organizations", membersRouter);
v1.route("/organizations", accountsRouter);
v1.route("/organizations", upiRouter);
v1.route("/organizations", qrRouter);
v1.route("/organizations", transactionsRouter);
v1.route("/organizations", syncRouter);
v1.route("/organizations", auditRouter);
v1.route("/notifications", notificationsRouter);
v1.route("/webhooks", webhooksRouter);

app.route("/api/v1", v1);
