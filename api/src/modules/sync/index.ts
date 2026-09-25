import { Hono } from "hono";
import { z } from "zod";
import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq, and, gt, desc } from "drizzle-orm";
import { generateId } from "../../lib/crypto.js";
import { requireAuth } from "../../middleware/auth.js";
import { requireTenant } from "../../middleware/tenant.js";
import type { AppEnv } from "../../types/hono.js";

export const syncRouter = new Hono<AppEnv>();

syncRouter.use("*", requireAuth);

/**
 * POST /api/v1/sync (or /api/v1/organizations/sync)
 * Multi-organization incremental sync endpoint.
 */
syncRouter.post("/", async (c) => {
  const userId = c.get("userId");
  const deviceId = c.req.header("x-device-id") || "default";
  const body = await c.req.json();

  const validator = z.object({
    organizations: z.array(
      z.object({
        organizationId: z.string().min(1),
        cursor: z.number().int().nonnegative().optional().default(0),
      })
    ),
  });

  const { organizations: requestedOrgs } = validator.parse(body);

  // Fetch active memberships for this user
  const activeMemberships = await db
    .select({ organizationId: schema.organizationMembers.organizationId })
    .from(schema.organizationMembers)
    .where(
      and(
        eq(schema.organizationMembers.userId, userId),
        eq(schema.organizationMembers.status, "ACTIVE")
      )
    )
    .all();

  const activeOrgSet = new Set(activeMemberships.map((m) => m.organizationId));
  const now = new Date();
  const results = [];

  for (const orgReq of requestedOrgs) {
    if (!activeOrgSet.has(orgReq.organizationId)) {
      continue; // Never return organization data for organizations user doesn't belong to
    }

    const orgId = orgReq.organizationId;
    const cursor = orgReq.cursor ?? 0;
    const limit = 100;

    const events = await db
      .select()
      .from(schema.outboxEvents)
      .where(
        and(
          eq(schema.outboxEvents.organizationId, orgId),
          gt(schema.outboxEvents.sequence, cursor)
        )
      )
      .limit(limit)
      .all();

    const nextCursor = events.length > 0 ? events[events.length - 1].sequence : cursor;

    // Update sync cursor
    const existingCursor = await db
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

    if (existingCursor) {
      if (nextCursor > existingCursor.lastSequence) {
        await db.update(schema.syncCursors)
          .set({ lastSequence: nextCursor, updatedAt: now })
          .where(eq(schema.syncCursors.id, existingCursor.id))
          .run();
      }
    } else {
      await db.insert(schema.syncCursors)
        .values({
          id: generateId("cur"),
          userId,
          deviceId,
          organizationId: orgId,
          lastSequence: nextCursor,
          updatedAt: now,
        })
        .run();
    }

    results.push({
      organizationId: orgId,
      nextCursor,
      hasMore: events.length === limit,
      changes: events.map((e: typeof schema.outboxEvents.$inferSelect) => {
        let payload: Record<string, unknown> = {};
        try {
          payload = JSON.parse(e.payloadJson);
        } catch (_) {}
        const entityId = (payload.id || payload.entityId || payload.memberId || payload.transactionId || e.id) as string;
        return {
          sequence: e.sequence,
          type: e.eventType,
          entityId,
          payload,
          createdAt: e.createdAt,
        };
      }),
    });
  }

  return c.json({
    success: true,
    organizations: results,
  });
});

syncRouter.get("/:orgId/sync", requireTenant, async (c) => {
  const orgId = c.get("organizationId");
  const userId = c.get("userId");
  const deviceId = c.req.header("x-device-id") || "default";
  const afterSequence = Number(c.req.query("afterSequence")) || 0;
  const limit = Math.min(Number(c.req.query("limit")) || 100, 250);

  // Fetch events strictly greater than afterSequence for this organization
  const events = await db
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
  const existingCursor = await db
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
      await db.update(schema.syncCursors)
        .set({ lastSequence: latestSequence, updatedAt: now })
        .where(eq(schema.syncCursors.id, existingCursor.id))
        .run();
    }
  } else {
    await db.insert(schema.syncCursors)
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
      events: events.map((e: typeof schema.outboxEvents.$inferSelect) => ({
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
