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
import { notificationsRouter, orgNotificationsRouter } from "./modules/notifications/index.js";
import { webhooksRouter } from "./modules/webhooks/index.js";
import { invitationsRouter, getMyInvitationsHandler } from "./modules/invitations/index.js";
import { devicesRouter } from "./modules/devices/index.js";
import { paymentAccountsRouter, paymentAppsRouter } from "./modules/paymentAccounts/index.js";
import { paymentEventsRouter } from "./modules/paymentEvents/index.js";
import { seedDemoMerchantData } from "./db/seed.js";
import { renderDashboardHtml } from "./dashboard/html.js";
import { requireAuth } from "./middleware/auth.js";
import { setD1Database } from "./db/index.js";
import type { AppEnv } from "./types/hono.js";

export const app = new Hono<AppEnv>();

// Initialize Cloudflare D1 database if present in environment
app.use("*", async (c, next) => {
  const d1 = (c.env as any)?.upi_easy_db || (c.env as any)?.DB;
  if (d1) {
    setD1Database(d1);
  }
  await next();
});

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

// Root Interactive Dashboard & API Map
app.get("/", (c) => {
  const accept = c.req.header("Accept") || "";
  if (accept.includes("application/json") && !accept.includes("text/html")) {
    return c.json({
      name: "UPI-Easy Multi-Tenant API",
      status: "online",
      version: "1.0.0",
      endpoints: {
        health: "/health",
        api: "/api/v1"
      }
    });
  }
  return c.html(renderDashboardHtml());
});

// Health check
app.get("/health", (c) => {
  return c.json({
    status: "healthy",
    timestamp: new Date().toISOString(),
    service: "upi-easy-api",
    version: "1.0.0",
  });
});

// API Routes Catalog
app.get("/api/routes", (c) => {
  return c.json({
    service: "upi-easy-api",
    versions: [
      {
        id: "v1",
        name: "Version 1 (Production)",
        basePath: "/api/v1",
        modules: [
          "auth", "organizations", "members", "accounts", "upi", "qr", "transactions", "sync", "notifications", "webhooks"
        ]
      },
      {
        id: "system",
        name: "System & Core",
        basePath: "/",
        modules: ["health", "routes"]
      }
    ]
  });
});

// Mount /api/v1 endpoints
const v1 = new Hono<AppEnv>();

v1.route("/auth", authRouter);
v1.route("/users", usersRouter);
v1.get("/me/invitations", requireAuth, getMyInvitationsHandler);
v1.route("/me", usersRouter);
v1.route("/invitations", invitationsRouter);
v1.route("/devices", devicesRouter);
v1.route("/sync", syncRouter);
v1.route("/organizations", organizationsRouter);
v1.route("/organizations", membersRouter);
v1.route("/organizations", accountsRouter);
v1.route("/organizations", upiRouter);
v1.route("/organizations", qrRouter);
v1.route("/organizations", transactionsRouter);
v1.route("/organizations", syncRouter);
v1.route("/organizations", auditRouter);
v1.route("/organizations", orgNotificationsRouter);
v1.route("/notifications", notificationsRouter);
v1.route("/payment-apps", paymentAppsRouter);
v1.route("/organizations", paymentAccountsRouter);
v1.route("/organizations", paymentEventsRouter);
v1.route("/webhooks", webhooksRouter);

// Development & Demo Seed Endpoint
v1.post("/dev/seed", async (c) => {
  seedDemoMerchantData();
  return c.json({ success: true, message: "Demo merchant dataset seeded successfully" });
});

app.route("/api/v1", v1);
