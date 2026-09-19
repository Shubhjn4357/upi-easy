import { describe, it, expect, beforeAll } from "vitest";
import { app } from "../src/app.js";
import { initDatabase } from "../src/db/index.js";

beforeAll(() => {
  initDatabase();
});

describe("Auth Module Integration Tests", () => {
  const testMobile = "9876543210";

  it("should sign in with Google Credential Manager and issue tokens", async () => {
    const res = await app.request("/api/v1/auth/google", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        idToken: "mock:test.user@gmail.com:google_sub_test_123",
        email: "test.user@gmail.com",
        fullName: "Test User",
        deviceId: "device-test-1",
      }),
    });

    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.user.email).toBe("test.user@gmail.com");
    expect(body.tokens.accessToken).toBeDefined();
    expect(body.tokens.refreshToken).toBeDefined();
  });

  it("should refresh access token using valid refresh token", async () => {
    const loginRes = await app.request("/api/v1/auth/google", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        idToken: "mock:refresh.test@gmail.com:google_sub_refresh",
        email: "refresh.test@gmail.com",
        fullName: "Refresh Tester",
      }),
    });
    const { tokens } = await loginRes.json();

    const refreshRes = await app.request("/api/v1/auth/refresh", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: tokens.refreshToken }),
    });

    expect(refreshRes.status).toBe(200);
    const refreshBody = await refreshRes.json();
    expect(refreshBody.success).toBe(true);
    expect(refreshBody.tokens.accessToken).toBeDefined();
  });

  it("should delete user account permanently via DELETE /api/v1/users/me", async () => {
    const loginRes = await app.request("/api/v1/auth/google", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        idToken: "mock:delete.me@gmail.com:google_sub_delete",
        email: "delete.me@gmail.com",
        fullName: "To Delete",
      }),
    });
    const { tokens } = await loginRes.json();

    const delRes = await app.request("/api/v1/users/me", {
      method: "DELETE",
      headers: { Authorization: `Bearer ${tokens.accessToken}` },
    });

    expect(delRes.status).toBe(200);
    const delBody = await delRes.json();
    expect(delBody.success).toBe(true);
  });

  it("should sign in with Google and complete onboarding setup", async () => {
    const testEmail = `merchant_${Date.now()}@gmail.com`;
    // 1. Google One Tap sign in
    const googleRes = await app.request("/api/v1/auth/google", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email: testEmail,
        fullName: "Google Merchant Owner",
        googleId: `google_${Date.now()}`,
      }),
    });

    expect(googleRes.status).toBe(200);
    const googleBody = await googleRes.json();
    expect(googleBody.success).toBe(true);
    expect(googleBody.isSetupComplete).toBe(false); // First time: setup not yet completed
    const token = googleBody.tokens.accessToken;

    // 2. Complete Startup Onboarding Setup Form
    const setupRes = await app.request("/api/v1/organizations/setup", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        businessName: "Krishna Supermarket",
        mobileNumber: "9876501234",
        primaryVpa: "krishna@okhdfcbank",
        payeeName: "Krishna Supermarket",
        bankName: "HDFC Bank",
        accountNumber: "50100234567890",
        ifscCode: "HDFC0001234",
      }),
    });

    expect(setupRes.status).toBe(201);
    const setupBody = await setupRes.json();
    expect(setupBody.success).toBe(true);
    expect(setupBody.organization.name).toBe("Krishna Supermarket");
    expect(setupBody.upiAccount.vpa).toBe("krishna@okhdfcbank");
    expect(setupBody.upiAccount.qrPayload).toContain("upi://pay?");
  });
});
