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
});
