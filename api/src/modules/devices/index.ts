import { Hono } from "hono";
import { z } from "zod";
import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq, and } from "drizzle-orm";
import { generateId } from "../../lib/crypto.js";
import { requireAuth } from "../../middleware/auth.js";
import type { AppEnv } from "../../types/hono.js";

export const devicesRouter = new Hono<AppEnv>();

devicesRouter.use("*", requireAuth);

/**
 * POST /api/v1/devices/register
 * Registers or updates an FCM token and device metadata for a user.
 */
devicesRouter.post("/register", async (c) => {
  const userId = c.get("userId");
  const body = await c.req.json();

  const validator = z.object({
    deviceId: z.string().min(1),
    platform: z.string().optional().default("ANDROID"),
    deviceModel: z.string().optional(),
    osVersion: z.string().optional(),
    appVersion: z.string().optional(),
    fcmToken: z.string().optional(),
  });

  const data = validator.parse(body);
  const now = new Date();

  // Find existing device record for (userId, deviceId)
  const existing = await db
    .select()
    .from(schema.devices)
    .where(
      and(
        eq(schema.devices.userId, userId),
        eq(schema.devices.deviceId, data.deviceId)
      )
    )
    .get();

  if (existing) {
    await db.update(schema.devices)
      .set({
        platform: data.platform,
        deviceModel: data.deviceModel ?? existing.deviceModel,
        osVersion: data.osVersion ?? existing.osVersion,
        appVersion: data.appVersion ?? existing.appVersion,
        fcmToken: data.fcmToken ?? existing.fcmToken,
        isActive: true,
        lastSeenAt: now,
        updatedAt: now,
      })
      .where(eq(schema.devices.id, existing.id))
      .run();

    return c.json({
      success: true,
      message: "Device updated successfully",
      deviceId: existing.id,
    });
  }

  const newId = generateId("dev");
  await db.insert(schema.devices)
    .values({
      id: newId,
      userId,
      deviceId: data.deviceId,
      platform: data.platform,
      deviceModel: data.deviceModel ?? null,
      osVersion: data.osVersion ?? null,
      appVersion: data.appVersion ?? null,
      fcmToken: data.fcmToken ?? null,
      isActive: true,
      lastSeenAt: now,
      createdAt: now,
      updatedAt: now,
    })
    .run();

  return c.json({
    success: true,
    message: "Device registered successfully",
    deviceId: newId,
  });
});

/**
 * POST /api/v1/devices/unregister
 * Marks a device session as inactive upon logout without deleting audit logs.
 */
devicesRouter.post("/unregister", async (c) => {
  const userId = c.get("userId");
  const body = await c.req.json();

  const validator = z.object({
    deviceId: z.string().min(1),
  });

  const { deviceId } = validator.parse(body);
  const now = new Date();

  await db.update(schema.devices)
    .set({
      isActive: false,
      updatedAt: now,
    })
    .where(
      and(
        eq(schema.devices.userId, userId),
        eq(schema.devices.deviceId, deviceId)
      )
    )
    .run();

  return c.json({
    success: true,
    message: "Device unregistered successfully",
  });
});
