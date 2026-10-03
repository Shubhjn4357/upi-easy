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
import { rolesRouter } from "./modules/roles/index.js";
import { invitationsRouter, getMyInvitationsHandler } from "./modules/invitations/index.js";
import { devicesRouter } from "./modules/devices/index.js";
import { paymentAccountsRouter, paymentAppsRouter } from "./modules/paymentAccounts/index.js";
import { paymentEventsRouter } from "./modules/paymentEvents/index.js";
import { seedDemoMerchantData } from "./db/seed.js";
import { renderDashboardHtml } from "./dashboard/html.js";
import { adminRouter } from "./modules/admin/index.js";
import { requireAuth } from "./middleware/auth.js";
import { requireAppSignature } from "./middleware/appSignature.js";
import { setD1Database } from "./db/index.js";
import { config } from "./config/index.js";
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
app.use(
  "*",
  secureHeaders({
    crossOriginOpenerPolicy: "same-origin-allow-popups",
  })
);
app.use(
  "*",
  cors({
    origin: "*",
    allowHeaders: [
      "Content-Type",
      "Authorization",
      "X-Organization-Id",
      "X-Device-Id",
      "Idempotency-Key",
      "x-app-timestamp",
      "x-app-signature",
      "X-App-Timestamp",
      "X-App-Signature",
    ],
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
  const getDashboard = () => {
    const clientId = c.env?.GOOGLE_WEB_CLIENT_ID || config.GOOGLE_WEB_CLIENT_ID;
    const apiBaseUrl = c.env?.API_BASE_URL || "";
    return renderDashboardHtml({ clientId, apiBaseUrl });
  };
  return c.html(getDashboard());
});

app.get("/dashboard", (c) => {
  const clientId = c.env?.GOOGLE_WEB_CLIENT_ID || config.GOOGLE_WEB_CLIENT_ID;
  const apiBaseUrl = c.env?.API_BASE_URL || "";
  return c.html(renderDashboardHtml({ clientId, apiBaseUrl }));
});

app.get("/admin", (c) => {
  const clientId = c.env?.GOOGLE_WEB_CLIENT_ID || config.GOOGLE_WEB_CLIENT_ID;
  const apiBaseUrl = c.env?.API_BASE_URL || "";
  return c.html(renderDashboardHtml({ clientId, apiBaseUrl }));
});

// Web Invite Landing Page (Deep links directly into Android app)
app.get("/invite/:token", async (c) => {
  const token = c.req.param("token");
  const email = c.req.query("email") || "";

  // Attempt to fetch invite details from DB
  let orgName = "Store";
  let role = "Staff Member";
  try {
    const invite = await (await import("./db/index.js")).db
      .select({
        role: (await import("./db/schema/index.js")).organizationInvites.role,
        orgName: (await import("./db/schema/index.js")).organizations.name,
      })
      .from((await import("./db/schema/index.js")).organizationInvites)
      .innerJoin(
        (await import("./db/schema/index.js")).organizations,
        (await import("drizzle-orm")).eq(
          (await import("./db/schema/index.js")).organizationInvites.organizationId,
          (await import("./db/schema/index.js")).organizations.id
        )
      )
      .where(
        (await import("drizzle-orm")).or(
          (await import("drizzle-orm")).eq((await import("./db/schema/index.js")).organizationInvites.token, token),
          (await import("drizzle-orm")).eq((await import("./db/schema/index.js")).organizationInvites.id, token)
        )
      )
      .get();

    if (invite) {
      orgName = invite.orgName;
      role = invite.role;
    }
  } catch (_) {}

  const deepLink = `upieasy://invite?token=${encodeURIComponent(token)}&email=${encodeURIComponent(email)}`;

  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Join ${orgName} on UPI-Easy</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
    body { background: #0A0D14; color: #F3F4F6; display: flex; align-items: center; justify-content: center; min-height: 100vh; padding: 20px; }
    .card { background: #131826; border: 1px solid #1F293D; border-radius: 24px; padding: 36px 28px; max-width: 420px; width: 100%; text-align: center; box-shadow: 0 20px 40px rgba(0,0,0,0.5); }
    .badge { display: inline-block; background: rgba(99, 102, 241, 0.15); color: #818CF8; border: 1px solid rgba(99, 102, 241, 0.3); padding: 6px 14px; border-radius: 999px; font-size: 13px; font-weight: 600; text-transform: uppercase; margin-bottom: 20px; }
    h1 { font-size: 24px; font-weight: 800; margin-bottom: 10px; color: #FFFFFF; }
    p { font-size: 15px; color: #9CA3AF; line-height: 1.5; margin-bottom: 28px; }
    .email-chip { background: #1E2638; border-radius: 12px; padding: 10px 14px; font-size: 14px; color: #E5E7EB; margin-bottom: 24px; word-break: break-all; }
    .btn { display: block; width: 100%; background: linear-gradient(135deg, #4F46E5, #6366F1); color: #FFF; font-weight: 700; font-size: 16px; padding: 15px; border-radius: 14px; text-decoration: none; border: none; cursor: pointer; transition: transform 0.1s, opacity 0.2s; box-shadow: 0 4px 14px rgba(79, 70, 229, 0.4); }
    .btn:hover { opacity: 0.95; transform: scale(1.01); }
    .subtext { margin-top: 20px; font-size: 13px; color: #6B7280; }
  </style>
  <script>
    // Auto launch deep link on mobile browsers
    window.onload = function() {
      setTimeout(function() {
        window.location.href = "${deepLink}";
      }, 300);
    };
  </script>
</head>
<body>
  <div class="card">
    <div class="badge">${role} Invitation</div>
    <h1>You're Invited!</h1>
    <p>You have been invited to join <strong>${orgName}</strong> on UPI-Easy as a <strong>${role}</strong>.</p>
    ${email ? `<div class="email-chip">Sign in with: <strong>${email}</strong></div>` : ""}
    <a href="${deepLink}" class="btn">Open in UPI-Easy App</a>
    <div class="subtext">Make sure the UPI-Easy app is installed on your Android device.</div>
  </div>
</body>
</html>`;

  return c.html(html);
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

// Enforce mutual app-server request signature authentication
v1.use("*", requireAppSignature);

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
v1.route("/organizations", rolesRouter);
v1.route("/admin", adminRouter);

// Development & Demo Seed Endpoint
v1.post("/dev/seed", async (c) => {
  seedDemoMerchantData();
  return c.json({ success: true, message: "Demo merchant dataset seeded successfully" });
});

app.route("/api/v1", v1);
