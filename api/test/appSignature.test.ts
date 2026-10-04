import { describe, it, expect } from "vitest";
import { app } from "../src/app.js";
import { config } from "../src/config/index.js";
import crypto from "node:crypto";

describe("App-Server Mutual Request Authentication Middleware", () => {
  const secretKey = config.API_SECRET_KEY;

  function generateSignature(timestamp: number, secret: string = secretKey): string {
    const input = `${timestamp}${secret}`;
    return crypto.createHash("sha256").update(input).digest("hex");
  }

  it("should accept request when x-app-timestamp and x-app-signature are valid", async () => {
    const timestamp = Math.floor(Date.now() / 1000);
    const signature = generateSignature(timestamp);

    const res = await app.request("/api/v1/payment-apps/supported", {
      method: "GET",
      headers: {
        "x-app-timestamp": timestamp.toString(),
        "x-app-signature": signature,
      },
    });

    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.data).toBeDefined();
    expect(Array.isArray(body.data)).toBe(true);
  });

  it("should reject request when x-app-timestamp is expired (> 60 seconds old)", async () => {
    const expiredTimestamp = Math.floor(Date.now() / 1000) - 120; // 2 minutes ago
    const signature = generateSignature(expiredTimestamp);

    const res = await app.request("/api/v1/payment-apps/supported", {
      method: "GET",
      headers: {
        "x-app-timestamp": expiredTimestamp.toString(),
        "x-app-signature": signature,
      },
    });

    expect(res.status).toBe(401);
    const body = await res.json();
    expect(body.error.code).toBe("REQUEST_EXPIRED");
  });

  it("should reject request when x-app-timestamp is too far in future (> 60 seconds)", async () => {
    const futureTimestamp = Math.floor(Date.now() / 1000) + 120; // 2 minutes in future
    const signature = generateSignature(futureTimestamp);

    const res = await app.request("/api/v1/payment-apps/supported", {
      method: "GET",
      headers: {
        "x-app-timestamp": futureTimestamp.toString(),
        "x-app-signature": signature,
      },
    });

    expect(res.status).toBe(401);
    const body = await res.json();
    expect(body.error.code).toBe("REQUEST_EXPIRED");
  });

  it("should reject request when signature is forged or generated with wrong secret", async () => {
    const timestamp = Math.floor(Date.now() / 1000);
    const forgedSignature = generateSignature(timestamp, "attacker_wrong_secret_key");

    const res = await app.request("/api/v1/payment-apps/supported", {
      method: "GET",
      headers: {
        "x-app-timestamp": timestamp.toString(),
        "x-app-signature": forgedSignature,
      },
    });

    expect(res.status).toBe(401);
    const body = await res.json();
    expect(body.error.code).toBe("INVALID_APP_SIGNATURE");
  });

  it("should reject request when signature header is missing but timestamp is provided", async () => {
    const timestamp = Math.floor(Date.now() / 1000);

    const res = await app.request("/api/v1/payment-apps/supported", {
      method: "GET",
      headers: {
        "x-app-timestamp": timestamp.toString(),
      },
    });

    expect(res.status).toBe(401);
    const body = await res.json();
    expect(body.error.code).toBe("MISSING_APP_SIGNATURE");
  });

  it("should reject request when timestamp header is invalid format", async () => {
    const res = await app.request("/api/v1/payment-apps/supported", {
      method: "GET",
      headers: {
        "x-app-timestamp": "not-a-number",
        "x-app-signature": "abcdef123456",
      },
    });

    expect(res.status).toBe(401);
    const body = await res.json();
    expect(body.error.code).toBe("INVALID_APP_TIMESTAMP");
  });

  it("should permit web Google auth requests without mobile app signature headers", async () => {
    const res = await app.request("/api/v1/auth/google", {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "origin": "https://upi-easy-api.aerotech.workers.dev",
      },
      body: JSON.stringify({
        idToken: "mock:webmerchant@test.com:google_sub_web123",
        email: "webmerchant@test.com",
        fullName: "Web Merchant",
        deviceId: "web-dashboard",
      }),
    });

    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.user.email).toBe("webmerchant@test.com");
    expect(body.tokens.accessToken).toBeDefined();
  });

  it("should permit authenticated web requests with Bearer token without mobile app signature headers", async () => {
    // 1. Sign in to obtain access token
    const loginRes = await app.request("/api/v1/auth/google", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        idToken: "mock:webauth@test.com:google_sub_webauth456",
        email: "webauth@test.com",
        fullName: "Web Auth",
      }),
    });
    const body = await loginRes.json();
    const token = body.tokens.accessToken;

    // 2. Access protected endpoint with Bearer token only (no x-app-* headers)
    const res = await app.request("/api/v1/users/me", {
      method: "GET",
      headers: {
        "authorization": `Bearer ${token}`,
      },
    });

    expect(res.status).toBe(200);
    const userBody = await res.json();
    expect(userBody.success).toBe(true);
  });
});

