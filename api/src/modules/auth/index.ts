import { Hono } from "hono";
import { z } from "zod";
import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq, and, gt, desc } from "drizzle-orm";
import { generateId, generateOtp, hashString } from "../../lib/crypto.js";
import { createAccessToken, createRefreshToken, verifyToken } from "../../lib/jwt.js";
import { AppError, UnauthorizedError, NotFoundError } from "../../lib/errors.js";
import { rateLimit } from "../../middleware/rateLimit.js";
import { requireAuth } from "../../middleware/auth.js";
import type { AppEnv } from "../../types/hono.js";

export const authRouter = new Hono<AppEnv>();

// Google One Tap / Credential Manager Sign-In Endpoint
authRouter.post("/google", async (c) => {
  const body = await c.req.json();
  const schemaValidator = z.object({
    idToken: z.string().optional(),
    googleId: z.string().optional(),
    email: z.string().email(),
    fullName: z.string().optional(),
    avatarUrl: z.string().optional(),
    deviceId: z.string().default("android-device"),
    deviceModel: z.string().optional(),
    osVersion: z.string().optional(),
    fcmToken: z.string().optional(),
  });

  const data = schemaValidator.parse(body);

  // Derive stable Google user ID
  const googleId = data.googleId || (data.idToken ? hashString(data.idToken).slice(0, 24) : `g_${hashString(data.email).slice(0, 16)}`);
  const now = new Date();

  // Find user by googleId or email
  let user = db
    .select()
    .from(schema.users)
    .where(eq(schema.users.email, data.email.toLowerCase()))
    .get();

  if (!user && googleId) {
    user = db
      .select()
      .from(schema.users)
      .where(eq(schema.users.googleId, googleId))
      .get();
  }

  if (!user) {
    const userId = generateId("usr");
    db.insert(schema.users)
      .values({
        id: userId,
        googleId,
        email: data.email.toLowerCase(),
        fullName: data.fullName ?? null,
        avatarUrl: data.avatarUrl ?? null,
        mobileNumber: null,
        status: "ACTIVE",
        createdAt: now,
        updatedAt: now,
      })
      .run();
    user = db.select().from(schema.users).where(eq(schema.users.id, userId)).get()!;
  } else if (!user.googleId) {
    db.update(schema.users)
      .set({ googleId, updatedAt: now })
      .where(eq(schema.users.id, user.id))
      .run();
  }

  // Register device
  let device = db
    .select()
    .from(schema.devices)
    .where(and(eq(schema.devices.userId, user.id), eq(schema.devices.deviceId, data.deviceId)))
    .get();

  if (!device) {
    const newDeviceId = generateId("dev");
    db.insert(schema.devices)
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
    device = db.select().from(schema.devices).where(eq(schema.devices.id, newDeviceId)).get()!;
  }

  // Create session
  const sessionId = generateId("sess");
  const sessionExpires = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);

  db.insert(schema.sessions)
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

  // Check if setup (organization + mobile + upi) is completed
  const memberships = db
    .select()
    .from(schema.organizationMembers)
    .where(and(eq(schema.organizationMembers.userId, user.id), eq(schema.organizationMembers.status, "ACTIVE")))
    .all();

  const isSetupComplete = memberships.length > 0 && !!user.mobileNumber;

  let defaultOrg = null;
  if (memberships.length > 0) {
    const org = db
      .select()
      .from(schema.organizations)
      .where(eq(schema.organizations.id, memberships[0].organizationId))
      .get();
    if (org) {
      defaultOrg = {
        id: org.id,
        name: org.name,
        role: "OWNER",
      };
    }
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
      expiresIn: 900,
    },
    isSetupComplete,
    defaultOrg,
  });
});

// Rate limit: 5 OTP requests per 15 minutes per mobile number
authRouter.post(
  "/request-otp",
  rateLimit({
    max: 5,
    windowMs: 15 * 60 * 1000,
    keyGenerator: (c) => `otp_${c.req.header("x-forwarded-for") || "ip"}`,
  }),
  async (c) => {
    const body = await c.req.json();
    const schemaValidator = z.object({
      mobileNumber: z.string().regex(/^[6-9]\d{9}$/, "Invalid 10-digit Indian mobile number"),
    });

    const { mobileNumber } = schemaValidator.parse(body);

    const otp = generateOtp();
    const otpHash = hashString(otp);
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000); // 5 minutes validity

    // Invalidate any previous pending OTPs for this mobile number
    db.delete(schema.otps).where(eq(schema.otps.mobileNumber, mobileNumber)).run();

    // Store hashed OTP
    db.insert(schema.otps)
      .values({
        id: generateId("otp"),
        mobileNumber,
        otpHash,
        attempts: 0,
        expiresAt,
        isVerified: false,
        createdAt: new Date(),
      })
      .run();

    // In production, dispatch through legitimate SMS provider.
    // In dev / test, return preview in response for seamless end-to-end flow.
    const isDev = process.env.NODE_ENV !== "production";

    return c.json({
      success: true,
      message: "OTP sent successfully to registered mobile number",
      expiresInSeconds: 300,
      ...(isDev ? { devOtpPreview: otp } : {}),
    });
  }
);

authRouter.post("/verify-otp", async (c) => {
  const body = await c.req.json();
  const schemaValidator = z.object({
    mobileNumber: z.string().regex(/^[6-9]\d{9}$/),
    otp: z.string().length(6),
    deviceId: z.string().default("default-device"),
    deviceModel: z.string().optional(),
    osVersion: z.string().optional(),
    fcmToken: z.string().optional(),
  });

  const { mobileNumber, otp, deviceId, deviceModel, osVersion, fcmToken } = schemaValidator.parse(body);
  const providedHash = hashString(otp);

  // Find latest active OTP
  const latestOtp = db
    .select()
    .from(schema.otps)
    .where(
      and(
        eq(schema.otps.mobileNumber, mobileNumber),
        eq(schema.otps.isVerified, false),
        gt(schema.otps.expiresAt, new Date())
      )
    )
    .orderBy(desc(schema.otps.createdAt))
    .get();

  if (!latestOtp) {
    throw new AppError("Invalid or expired OTP", 400, "INVALID_OTP");
  }

  if (latestOtp.attempts >= 3) {
    throw new AppError("Too many failed attempts. Please request a new OTP", 400, "MAX_OTP_ATTEMPTS");
  }

  if (latestOtp.otpHash !== providedHash) {
    db.update(schema.otps)
      .set({ attempts: latestOtp.attempts + 1 })
      .where(eq(schema.otps.id, latestOtp.id))
      .run();
    throw new AppError("Incorrect OTP entered", 400, "INCORRECT_OTP");
  }

  // Mark OTP as verified
  db.update(schema.otps)
    .set({ isVerified: true })
    .where(eq(schema.otps.id, latestOtp.id))
    .run();

  // Find or create user
  let user = db.select().from(schema.users).where(eq(schema.users.mobileNumber, mobileNumber)).get();
  const now = new Date();

  if (!user) {
    const userId = generateId("usr");
    db.insert(schema.users)
      .values({
        id: userId,
        mobileNumber,
        status: "ACTIVE",
        createdAt: now,
        updatedAt: now,
      })
      .run();
    user = db.select().from(schema.users).where(eq(schema.users.id, userId)).get()!;
  }

  // Register / update device
  let device = db
    .select()
    .from(schema.devices)
    .where(and(eq(schema.devices.userId, user.id), eq(schema.devices.deviceId, deviceId)))
    .get();

  if (!device) {
    const newDeviceId = generateId("dev");
    db.insert(schema.devices)
      .values({
        id: newDeviceId,
        userId: user.id,
        deviceId,
        deviceModel: deviceModel ?? null,
        osVersion: osVersion ?? null,
        fcmToken: fcmToken ?? null,
        isActive: true,
        lastSeenAt: now,
        createdAt: now,
      })
      .run();
    device = db.select().from(schema.devices).where(eq(schema.devices.id, newDeviceId)).get()!;
  } else {
    db.update(schema.devices)
      .set({
        deviceModel: deviceModel ?? device.deviceModel,
        osVersion: osVersion ?? device.osVersion,
        fcmToken: fcmToken ?? device.fcmToken,
        lastSeenAt: now,
      })
      .where(eq(schema.devices.id, device.id))
      .run();
  }

  // Create session
  const sessionId = generateId("sess");
  const sessionExpires = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000); // 30 days

  db.insert(schema.sessions)
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

  // Record audit log
  db.insert(schema.auditLogs)
    .values({
      id: generateId("aud"),
      organizationId: null,
      actorId: user.id,
      action: "user.login",
      resourceType: "user",
      resourceId: user.id,
      metadataJson: JSON.stringify({ deviceId, mobileNumber }),
      ipAddress: c.req.header("x-forwarded-for") || "unknown",
      deviceId,
      createdAt: now,
    })
    .run();

  return c.json({
    success: true,
    user: {
      id: user.id,
      mobileNumber: user.mobileNumber,
      fullName: user.fullName,
      email: user.email,
      status: user.status,
    },
    tokens: {
      accessToken,
      refreshToken,
      expiresIn: 900, // 15 mins
    },
  });
});

authRouter.post("/refresh", async (c) => {
  const body = await c.req.json();
  const schemaValidator = z.object({
    refreshToken: z.string(),
  });
  const { refreshToken } = schemaValidator.parse(body);

  let payload: any;
  try {
    payload = await verifyToken(refreshToken);
  } catch {
    throw new UnauthorizedError("Invalid or expired refresh token");
  }

  if (payload.type !== "refresh") {
    throw new UnauthorizedError("Invalid token type");
  }

  const session = db
    .select()
    .from(schema.sessions)
    .where(and(eq(schema.sessions.id, payload.sessionId), eq(schema.sessions.isRevoked, false)))
    .get();

  if (!session) {
    throw new UnauthorizedError("Session has been revoked");
  }

  const user = db.select().from(schema.users).where(eq(schema.users.id, session.userId)).get();
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
      expiresIn: 900,
    },
  });
});

authRouter.post("/logout", requireAuth, async (c) => {
  const sessionId = c.get("sessionId");
  db.update(schema.sessions)
    .set({ isRevoked: true, updatedAt: new Date() })
    .where(eq(schema.sessions.id, sessionId))
    .run();

  return c.json({ success: true, message: "Logged out successfully" });
});
