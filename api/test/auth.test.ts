import { describe, it, expect, beforeAll } from "vitest";
import { app } from "../src/app.js";
import { initDatabase } from "../src/db/index.js";

beforeAll(() => {
  initDatabase();
});

describe("Auth Module Integration Tests", () => {
  const testMobile = "9876543210";

  it("should request OTP successfully", async () => {
    const res = await app.request("/api/v1/auth/request-otp", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mobileNumber: testMobile }),
    });

    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.expiresInSeconds).toBe(300);
    expect(body.devOtpPreview).toBeDefined();
  });

  it("should fail OTP verification with incorrect OTP", async () => {
    const res = await app.request("/api/v1/auth/verify-otp", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        mobileNumber: testMobile,
        otp: "000000",
      }),
    });

    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.error.code).toBe("INCORRECT_OTP");
  });

  it("should verify OTP and issue tokens successfully", async () => {
    // Request fresh OTP
    const reqRes = await app.request("/api/v1/auth/request-otp", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mobileNumber: testMobile }),
    });
    const { devOtpPreview } = await reqRes.json();

    // Verify
    const verifyRes = await app.request("/api/v1/auth/verify-otp", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        mobileNumber: testMobile,
        otp: devOtpPreview,
        deviceId: "test-device-1",
      }),
    });

    expect(verifyRes.status).toBe(200);
    const verifyBody = await verifyRes.json();
    expect(verifyBody.success).toBe(true);
    expect(verifyBody.user.mobileNumber).toBe(testMobile);
    expect(verifyBody.tokens.accessToken).toBeDefined();
    expect(verifyBody.tokens.refreshToken).toBeDefined();
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
