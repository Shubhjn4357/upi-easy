import { Hono } from "hono";
import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq, and, gt, desc } from "drizzle-orm";
import { generateId } from "../../lib/crypto.js";
import { requireAuth } from "../../middleware/auth.js";
import { requireTenant } from "../../middleware/tenant.js";
import type { AppEnv } from "../../types/hono.js";

export const syncRouter = new Hono<AppEnv>();

syncRouter.use("*", requireAuth);

syncRouter.get("/:orgId/sync", requireTenant, async (c) => {
  const orgId = c.get("organizationId");
  const userId = c.get("userId");
  const deviceId = c.req.header("x-device-id") || "default";
  const afterSequence = Number(c.req.query("afterSequence")) || 0;
  const limit = Math.min(Number(c.req.query("limit")) || 100, 250);

  // Fetch events strictly greater than afterSequence for this organization
  const events = db
    .select()
    .from(schema.outboxEvents)
    .where(
      and(
        eq(schema.outboxEvents.organizationId, orgId),
        gt(schema.outboxEvents.sequence, afterSequence)
      )
    )
    .limit(limit)
    .all();

  // Find latest sequence in the returned events, or fallback to afterSequence
  const latestSequence = events.length > 0 ? events[events.length - 1].sequence : afterSequence;

  // Update sync cursor for this user and device
  const existingCursor = db
    .select()
    .from(schema.syncCursors)
    .where(
      and(
        eq(schema.syncCursors.organizationId, orgId),
        eq(schema.syncCursors.userId, userId),
        eq(schema.syncCursors.deviceId, deviceId)
      )
    )
    .get();

  const now = new Date();
  if (existingCursor) {
    if (latestSequence > existingCursor.lastSequence) {
      db.update(schema.syncCursors)
        .set({ lastSequence: latestSequence, updatedAt: now })
        .where(eq(schema.syncCursors.id, existingCursor.id))
        .run();
    }
  } else {
    db.insert(schema.syncCursors)
      .values({
        id: generateId("cur"),
        userId,
        deviceId,
        organizationId: orgId,
        lastSequence: latestSequence,
        updatedAt: now,
      })
      .run();
  }

  return c.json({
    success: true,
    data: {
      events: events.map((e) => ({
        sequence: e.sequence,
        id: e.id,
        eventType: e.eventType,
        payload: JSON.parse(e.payloadJson),
        createdAt: e.createdAt,
      })),
      hasMore: events.length === limit,
      latestSequence,
    },
  });
});
