import { describe, it, expect, beforeAll } from "vitest";
import { app } from "../src/app.js";
import { initDatabase, db } from "../src/db/index.js";
import * as schema from "../src/db/schema/index.js";
import { eq } from "drizzle-orm";
import { verifyGoogleIdToken } from "../src/lib/googleAuth.js";

describe("Google Credential Manager Server-Side Verification Tests", () => {
  beforeAll(() => {
    initDatabase();
  });

  it("should extract verified identity from dev/mock Google ID token", async () => {
    const verified = await verifyGoogleIdToken("mock:merchant.verified@gmail.com:google_sub_109283741", {
      nonce: "test_secure_nonce_123",
    });

    expect(verified.sub).toBe("google_sub_109283741");
    expect(verified.email).toBe("merchant.verified@gmail.com");
    expect(verified.emailVerified).toBe(true);
    expect(verified.nonce).toBe("test_secure_nonce_123");
  });

  it("should sign in and link user account via POST /api/v1/auth/google", async () => {
    const uniqueEmail = `google_user_${Date.now()}@gmail.com`;
    const uniqueSub = `sub_${Date.now()}`;
    const nonce = "cryptographic_nonce_abc_123";

    const res = await app.request("/api/v1/auth/google", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        idToken: `mock:${uniqueEmail}:${uniqueSub}`,
        nonce,
        deviceId: "device_test_model",
        deviceModel: "Pixel 8 Pro",
        osVersion: "Android 14",
      }),
    });

    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.user.email).toBe(uniqueEmail);
    expect(body.tokens.accessToken).toBeDefined();
    expect(body.tokens.refreshToken).toBeDefined();

    // Verify user record in database
    const userInDb = db.select().from(schema.users).where(eq(schema.users.googleId, uniqueSub)).get();
    expect(userInDb).toBeDefined();
    expect(userInDb?.email).toBe(uniqueEmail);

    // Verify device record
    const deviceInDb = db.select().from(schema.devices).where(eq(schema.devices.userId, userInDb!.id)).get();
    expect(deviceInDb).toBeDefined();
    expect(deviceInDb?.deviceModel).toBe("Pixel 8 Pro");
  });

  it("should link existing user when same email signs in with Google", async () => {
    const commonEmail = `existing_merchant_${Date.now()}@gmail.com`;
    const now = new Date();

    // User originally created via Mobile OTP with email set
    db.insert(schema.users).values({
      id: `usr_mobile_${Date.now()}`,
      mobileNumber: "9112233445",
      email: commonEmail,
      fullName: "Original Owner",
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    }).run();

    const googleSub = `google_linked_sub_${Date.now()}`;

    // Now signs in with Google Credential Manager
    const res = await app.request("/api/v1/auth/google", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        idToken: `mock:${commonEmail}:${googleSub}`,
        deviceId: "android_device_link",
      }),
    });

    expect(res.status).toBe(200);

    // Verify googleId was linked to original user record
    const user = db.select().from(schema.users).where(eq(schema.users.email, commonEmail)).get();
    expect(user).toBeDefined();
    expect(user?.googleId).toBe(googleSub);
    expect(user?.mobileNumber).toBe("9112233445");
  });
});
