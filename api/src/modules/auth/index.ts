import { Hono } from "hono";
import { z } from "zod";
import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq, and, or } from "drizzle-orm";
import { generateId } from "../../lib/crypto.js";
import { createAccessToken, createRefreshToken, verifyToken } from "../../lib/jwt.js";
import { AppError, UnauthorizedError, NotFoundError } from "../../lib/errors.js";
import { rateLimit } from "../../middleware/rateLimit.js";
import { requireAuth } from "../../middleware/auth.js";
import type { AppEnv } from "../../types/hono.js";
import { verifyGoogleIdToken } from "../../lib/googleAuth.js";
import { config } from "../../config/index.js";

export const authRouter = new Hono<AppEnv>();


// Google Credential Manager Server-Side Verified Sign-In Endpoint
authRouter.post("/google", async (c) => {
  const body = await c.req.json();
  const schemaValidator = z.object({
    idToken: z.string().optional().default("mock_google_token"),
    nonce: z.string().optional(),
    deviceId: z.string().default("android-device"),
    deviceModel: z.string().optional(),
    osVersion: z.string().optional(),
    fcmToken: z.string().optional(),
    // Backward-compatible test overrides when in test environment
    email: z.string().email().optional(),
    fullName: z.string().optional(),
    avatarUrl: z.string().optional(),
  });

  const data = schemaValidator.parse(body);

  // 1. Cryptographically verify Google ID Token with Google JWKS (aud, iss, exp, nonce)
  let verified;
  try {
    const googleClientId = c.env?.GOOGLE_WEB_CLIENT_ID || config.GOOGLE_WEB_CLIENT_ID;
    verified = await verifyGoogleIdToken(data.idToken, {
      clientId: googleClientId,
      nonce: data.nonce,
      expectedEmail: data.email,
    });
  } catch (_err: unknown) {
    // If in test environment and a mock email was provided
    if (process.env.NODE_ENV === "test" && data.email) {
      verified = {
        sub: `google_sub_${data.email.replace(/[^a-zA-Z0-9]/g, "")}`,
        email: data.email.toLowerCase(),
        emailVerified: true,
        name: data.fullName ?? "Test User",
        picture: data.avatarUrl ?? null,
      };
    } else {
      throw _err;
    }
  }

  const googleSub = verified.sub;
  const verifiedEmail = verified.email.toLowerCase();
  const displayName = verified.name || data.fullName || null;
  const displayAvatar = verified.picture || data.avatarUrl || null;
  const now = new Date();

  // 2. Account Linking Policy:
  // First, look up by stable google.sub
  let user = await db
    .select()
    .from(schema.users)
    .where(eq(schema.users.googleId, googleSub))
    .get();

  // Second, look up by verified email if not already linked to a Google ID
  if (!user) {
    user = await db
      .select()
      .from(schema.users)
      .where(eq(schema.users.email, verifiedEmail))
      .get();

    if (user) {
      // Link verified Google identity to existing user
      await db.update(schema.users)
        .set({
          googleId: googleSub,
          fullName: user.fullName || displayName,
          avatarUrl: user.avatarUrl || displayAvatar,
          updatedAt: now,
        })
        .where(eq(schema.users.id, user.id))
        .run();
    }
  }

  // Third, create new user if neither google.sub nor email exists
  if (!user) {
    const userId = generateId("usr");
    await db.insert(schema.users)
      .values({
        id: userId,
        googleId: googleSub,
        email: verifiedEmail,
        fullName: displayName,
        avatarUrl: displayAvatar,
        mobileNumber: null,
        status: "ACTIVE",
        createdAt: now,
        updatedAt: now,
      })
      .run();
    user = (await db.select().from(schema.users).where(eq(schema.users.id, userId)).get())!;
  }

  // 3. Auto-accept any pending organization invitations for this user (by email or mobile)
  try {
    const inviteConditions = [];
    if (verifiedEmail) {
      inviteConditions.push(eq(schema.organizationInvites.invitedEmail, verifiedEmail));
    }
    if (user.mobileNumber) {
      inviteConditions.push(eq(schema.organizationInvites.invitedMobile, user.mobileNumber));
    }
    inviteConditions.push(eq(schema.organizationInvites.invitedUserId, user.id));

    if (inviteConditions.length > 0) {
      const pendingInvites = await db
        .select()
        .from(schema.organizationInvites)
        .where(
          and(
            eq(schema.organizationInvites.status, "PENDING"),
            or(...inviteConditions)
          )
        )
        .all();

      for (const inv of pendingInvites) {
        if (new Date(inv.expiresAt) <= now) {
          await db.update(schema.organizationInvites)
            .set({ status: "EXPIRED", updatedAt: now })
            .where(eq(schema.organizationInvites.id, inv.id))
            .run();
          continue;
        }
        // Ensure not already a member
        const existingMember = await db
          .select()
          .from(schema.organizationMembers)
          .where(
            and(
              eq(schema.organizationMembers.organizationId, inv.organizationId),
              eq(schema.organizationMembers.userId, user.id)
            )
          )
          .get();

        if (!existingMember) {
          let roleRecord = await db.select().from(schema.roles).where(eq(schema.roles.name, inv.role)).get();
          if (!roleRecord) {
            roleRecord = await db.select().from(schema.roles).where(eq(schema.roles.id, `role_${inv.role.toLowerCase()}`)).get();
          }
          const roleId = roleRecord?.id || "role_cashier";

          await db.insert(schema.organizationMembers)
            .values({
              id: generateId("mem"),
              organizationId: inv.organizationId,
              userId: user.id,
              roleId,
              status: "ACTIVE",
              invitedBy: inv.invitedBy,
              joinedAt: now,
              createdAt: now,
              updatedAt: now,
            })
            .run();
        }

        await db.update(schema.organizationInvites)
          .set({
            status: "ACCEPTED",
            acceptedAt: now,
            invitedUserId: user.id,
            updatedAt: now,
          })
          .where(eq(schema.organizationInvites.id, inv.id))
          .run();
      }
    }
  } catch (inviteErr) {
    console.warn("[Auth] Error linking pending invites:", inviteErr);
  }

  // Register device
  let device = await db
    .select()
    .from(schema.devices)
    .where(and(eq(schema.devices.userId, user.id), eq(schema.devices.deviceId, data.deviceId)))
    .get();

  if (!device) {
    const newDeviceId = generateId("dev");
    await db.insert(schema.devices)
      .values({
        id: newDeviceId,
        userId: user.id,
        deviceId: data.deviceId,
        deviceModel: data.deviceModel ?? null,
        osVersion: data.osVersion ?? null,
        fcmToken: data.fcmToken ?? null,
        isActive: true,
        lastSeenAt: now,
        createdAt: now,
      })
      .run();
    device = (await db.select().from(schema.devices).where(eq(schema.devices.id, newDeviceId)).get())!;
  }

  // Create session
  const sessionId = generateId("sess");
  const sessionExpires = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);

  await db.insert(schema.sessions)
    .values({
      id: sessionId,
      userId: user.id,
      deviceId: device.id,
      expiresAt: sessionExpires,
      isRevoked: false,
      ipAddress: c.req.header("x-forwarded-for") || "unknown",
      userAgent: c.req.header("user-agent") || "unknown",
      createdAt: now,
      updatedAt: now,
    })
    .run();

  // Issue tokens
  const accessToken = await createAccessToken({
    sub: user.id,
    mobileNumber: user.mobileNumber || "",
    sessionId,
  });
  const refreshToken = await createRefreshToken(user.id, sessionId);

  // Check active organization memberships
  const memberships = await db
    .select({
      id: schema.organizationMembers.id,
      organizationId: schema.organizationMembers.organizationId,
      roleId: schema.organizationMembers.roleId,
      status: schema.organizationMembers.status,
      roleName: schema.roles.name,
      orgName: schema.organizations.name,
      legalBusinessName: schema.organizations.legalBusinessName,
      category: schema.organizations.category,
      panNumber: schema.organizations.panNumber,
      gstin: schema.organizations.gstin,
    })
    .from(schema.organizationMembers)
    .innerJoin(schema.organizations, eq(schema.organizationMembers.organizationId, schema.organizations.id))
    .leftJoin(schema.roles, eq(schema.organizationMembers.roleId, schema.roles.id))
    .where(and(eq(schema.organizationMembers.userId, user.id), eq(schema.organizationMembers.status, "ACTIVE")))
    .all();

  // If user already belongs to an active organization, onboarding is COMPLETE! Skip company creation
  const isSetupComplete = memberships.length > 0;

  let defaultOrg = null;
  if (memberships.length > 0) {
    const primary = memberships[0];
    const role = primary.roleName || "MEMBER";

    // Fetch granular permissions for this role
    let permissions: string[] = [];
    if (role === "OWNER") {
      permissions = ["*"];
    } else {
      const permRows = await db
        .select({ name: schema.permissions.name })
        .from(schema.rolePermissions)
        .innerJoin(schema.permissions, eq(schema.rolePermissions.permissionId, schema.permissions.id))
        .where(eq(schema.rolePermissions.roleId, primary.roleId))
        .all();
      permissions = permRows.map((p: { name: string }) => p.name);
    }

    defaultOrg = {
      id: primary.organizationId,
      name: primary.orgName,
      legalBusinessName: primary.legalBusinessName,
      category: primary.category,
      panNumber: primary.panNumber,
      gstin: primary.gstin,
      role,
      status: primary.status,
      permissions,
    };
  }

  return c.json({
    success: true,
    user: {
      id: user.id,
      email: user.email,
      fullName: user.fullName,
      mobileNumber: user.mobileNumber,
      avatarUrl: user.avatarUrl,
    },
    tokens: {
      accessToken,
      refreshToken,
      expiresIn: 604800,
    },
    isSetupComplete,
    defaultOrg,
  });
});

authRouter.post("/refresh", async (c) => {
  const body = await c.req.json();
  const schemaValidator = z.object({
    refreshToken: z.string(),
  });
  const { refreshToken } = schemaValidator.parse(body);

  interface RefreshPayload {
    sub: string;
    sessionId: string;
    type?: string;
  }
  let payload: RefreshPayload;
  try {
    payload = await verifyToken<RefreshPayload>(refreshToken);
  } catch {
    throw new UnauthorizedError("Invalid or expired refresh token");
  }

  if (payload.type !== "refresh") {
    throw new UnauthorizedError("Invalid token type");
  }

  const session = await db
    .select()
    .from(schema.sessions)
    .where(and(eq(schema.sessions.id, payload.sessionId), eq(schema.sessions.isRevoked, false)))
    .get();

  if (!session) {
    throw new UnauthorizedError("Session has been revoked");
  }

  const user = await db.select().from(schema.users).where(eq(schema.users.id, session.userId)).get();
  if (!user || user.status !== "ACTIVE") {
    throw new UnauthorizedError("User is suspended or inactive");
  }

  // Issue new access token
  const accessToken = await createAccessToken({
    sub: user.id,
    mobileNumber: user.mobileNumber || "",
    sessionId: session.id,
  });

  return c.json({
    success: true,
    tokens: {
      accessToken,
      expiresIn: 604800,
    },
  });
});

authRouter.post("/logout", requireAuth, async (c) => {
  const sessionId = c.get("sessionId");
  await db.update(schema.sessions)
    .set({ isRevoked: true, updatedAt: new Date() })
    .where(eq(schema.sessions.id, sessionId))
    .run();

  return c.json({ success: true, message: "Logged out successfully" });
});
