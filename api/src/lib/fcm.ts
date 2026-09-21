import { db } from "../db/index.js";
import * as schema from "../db/schema/index.js";
import { eq, and, inArray } from "drizzle-orm";
import { generateId } from "./crypto.js";

export interface FcmPayload {
  title: string;
  body: string;
  data?: Record<string, string>;
}

export interface NotifyOrgMembersOptions {
  organizationId: string;
  type: string;
  title: string;
  body: string;
  data?: Record<string, string>;
  requiredPermission?: string;
  excludeUserId?: string;
}

/**
 * Sends a push notification to a specific device via FCM.
 * Gracefully logs and handles delivery errors or inactive tokens.
 */
export async function sendFcmToDevice(
  fcmToken: string,
  payload: FcmPayload
): Promise<boolean> {
  if (!fcmToken || fcmToken.trim().length === 0) {
    return false;
  }

  // In production with Cloudflare Worker, this can call Google OAuth2 FCM v1 endpoint.
  // For local and tests, we simulate successful delivery and log for audit.
  try {
    const fcmServerKey = process.env.FCM_SERVER_KEY;
    if (fcmServerKey && process.env.NODE_ENV === "production") {
      const res = await fetch("https://fcm.googleapis.com/fcm/send", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `key=${fcmServerKey}`,
        },
        body: JSON.stringify({
          to: fcmToken,
          notification: {
            title: payload.title,
            body: payload.body,
          },
          data: payload.data || {},
          priority: "high",
        }),
      });

      if (!res.ok) {
        const errorText = await res.text();
        console.warn(`[FCM] Failed to send push: ${errorText}`);
        return false;
      }
      return true;
    }

    // Development / Test simulation
    return true;
  } catch (err: any) {
    console.error(`[FCM] Error dispatching push notification:`, err?.message || err);
    return false;
  }
}

/**
 * Dispatches in-app notifications and FCM push notifications to all authorized
 * members and their active registered devices in an organization.
 */
export async function notifyOrganizationMembers(options: NotifyOrgMembersOptions): Promise<{ notifiedUsers: number; notifiedDevices: number }> {
  const { organizationId, type, title, body, data = {}, excludeUserId } = options;
  const now = new Date();

  // 1. Fetch active members for this organization
  const membersQuery = db
    .select({
      userId: schema.organizationMembers.userId,
      roleId: schema.organizationMembers.roleId,
    })
    .from(schema.organizationMembers)
    .where(
      and(
        eq(schema.organizationMembers.organizationId, organizationId),
        eq(schema.organizationMembers.status, "ACTIVE")
      )
    );

  const members = await membersQuery.all();
  const eligibleMembers = members.filter((m) => m.userId !== excludeUserId);

  if (eligibleMembers.length === 0) {
    return { notifiedUsers: 0, notifiedDevices: 0 };
  }

  const userIds = eligibleMembers.map((m) => m.userId);

  // 2. Fetch notification preferences for these members
  const preferences = await db
    .select()
    .from(schema.notificationPreferences)
    .where(
      and(
        eq(schema.notificationPreferences.organizationId, organizationId),
        inArray(schema.notificationPreferences.userId, userIds)
      )
    )
    .all();

  const prefMap = new Map(preferences.map((p) => [p.userId, p]));

  // Filter users based on preference type
  const isPaymentType = type.startsWith("payment.") || type.startsWith("PAYMENT_");
  const isStaffType = type.startsWith("staff.") || type.startsWith("STAFF_");

  const allowedUserIds = userIds.filter((uid) => {
    const pref = prefMap.get(uid);
    if (!pref) return true; // default true

    if (isPaymentType) {
      return pref.paymentReceived ?? pref.paymentAlerts ?? true;
    }
    if (isStaffType) {
      return pref.staffActivity ?? pref.staffAlerts ?? true;
    }
    return true;
  });

  if (allowedUserIds.length === 0) {
    return { notifiedUsers: 0, notifiedDevices: 0 };
  }

  // 3. Create in-app notification records for each recipient
  for (const uid of allowedUserIds) {
    try {
      await db.insert(schema.notifications)
        .values({
          id: generateId("notif"),
          userId: uid,
          organizationId,
          title,
          message: body,
          type,
          isRead: false,
          metadataJson: JSON.stringify(data),
          createdAt: now,
        })
        .run();
    } catch (e: any) {
      console.warn(`[Notification] In-app record creation error:`, e?.message);
    }
  }

  // 4. Fetch all active devices belonging to these users
  const activeDevices = await db
    .select()
    .from(schema.devices)
    .where(
      and(
        inArray(schema.devices.userId, allowedUserIds),
        eq(schema.devices.isActive, true)
      )
    )
    .all();

  let devicesNotified = 0;
  for (const dev of activeDevices) {
    if (dev.fcmToken) {
      await sendFcmToDevice(dev.fcmToken, { title, body, data });
      devicesNotified++;
    }
  }

  return { notifiedUsers: allowedUserIds.length, notifiedDevices: devicesNotified };
}
