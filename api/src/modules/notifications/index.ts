import { Hono } from "hono";
import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq, and, desc } from "drizzle-orm";
import { requireAuth } from "../../middleware/auth.js";
import type { AppEnv } from "../../types/hono.js";

export const notificationsRouter = new Hono<AppEnv>();

notificationsRouter.use("*", requireAuth);

notificationsRouter.get("/", async (c) => {
  const userId = c.get("userId");
  const limit = Math.min(Number(c.req.query("limit")) || 20, 50);

  const notifs = await db
    .select()
    .from(schema.notifications)
    .where(eq(schema.notifications.userId, userId))
    .orderBy(desc(schema.notifications.createdAt))
    .limit(limit)
    .all();

  return c.json({ success: true, notifications: notifs });
});

notificationsRouter.patch("/:id/read", async (c) => {
  const userId = c.get("userId");
  const id = c.req.param("id");

  await db.update(schema.notifications)
    .set({ isRead: true })
    .where(and(eq(schema.notifications.id, id), eq(schema.notifications.userId, userId)))
    .run();

  return c.json({ success: true, message: "Marked as read" });
});

export const orgNotificationsRouter = new Hono<AppEnv>();
orgNotificationsRouter.use("*", requireAuth);

orgNotificationsRouter.get("/:organizationId/notifications", async (c) => {
  const userId = c.get("userId");
  const organizationId = c.req.param("organizationId");
  const limit = Math.min(Number(c.req.query("limit")) || 20, 50);

  const notifs = await db
    .select()
    .from(schema.notifications)
    .where(and(eq(schema.notifications.organizationId, organizationId), eq(schema.notifications.userId, userId)))
    .orderBy(desc(schema.notifications.createdAt))
    .limit(limit)
    .all();

  return c.json({ success: true, notifications: notifs });
});

orgNotificationsRouter.post("/:organizationId/notifications/:id/read", async (c) => {
  const userId = c.get("userId");
  const id = c.req.param("id");

  await db.update(schema.notifications)
    .set({ isRead: true })
    .where(and(eq(schema.notifications.id, id), eq(schema.notifications.userId, userId)))
    .run();

  return c.json({ success: true, message: "Marked as read" });
});

orgNotificationsRouter.patch("/:organizationId/notifications/:id/read", async (c) => {
  const userId = c.get("userId");
  const id = c.req.param("id");

  await db.update(schema.notifications)
    .set({ isRead: true })
    .where(and(eq(schema.notifications.id, id), eq(schema.notifications.userId, userId)))
    .run();

  return c.json({ success: true, message: "Marked as read" });
});

