import { describe, it, expect, beforeAll } from "vitest";
import { app } from "../src/app.js";
import { initDatabase } from "../src/db/index.js";

beforeAll(() => {
  initDatabase();
});

describe("RBAC Server-Enforced Permission Tests", () => {
  let ownerToken: string;
  let cashierToken: string;
  let orgId: string;

  beforeAll(async () => {
    // Owner setup
    const reqO = await app.request("/api/v1/auth/request-otp", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mobileNumber: "9333333333" }),
    });
    const { devOtpPreview: otpO } = await reqO.json();
    const verO = await app.request("/api/v1/auth/verify-otp", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mobileNumber: "9333333333", otp: otpO }),
    });
    ownerToken = (await verO.json()).tokens.accessToken;

    // Create Org
    const createOrg = await app.request("/api/v1/organizations", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${ownerToken}`,
      },
      body: JSON.stringify({ name: "RBAC Test Org" }),
    });
    orgId = (await createOrg.json()).organization.id;

    // Owner invites Cashier
    await app.request(`/api/v1/organizations/${orgId}/staff/invite`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${ownerToken}`,
      },
      body: JSON.stringify({
        mobileNumber: "9444444444",
        fullName: "Test Cashier",
        role: "CASHIER",
      }),
    });

    // Cashier logs in
    const reqC = await app.request("/api/v1/auth/request-otp", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mobileNumber: "9444444444" }),
    });
    const { devOtpPreview: otpC } = await reqC.json();
    const verC = await app.request("/api/v1/auth/verify-otp", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mobileNumber: "9444444444", otp: otpC }),
    });
    cashierToken = (await verC.json()).tokens.accessToken;
  });

  it("Cashier can read transactions in their organization", async () => {
    const res = await app.request(`/api/v1/organizations/${orgId}/transactions`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${cashierToken}`,
      },
    });

    expect(res.status).toBe(200);
  });

  it("Cashier CANNOT invite staff (should return 403 Forbidden)", async () => {
    const res = await app.request(`/api/v1/organizations/${orgId}/staff/invite`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${cashierToken}`,
      },
      body: JSON.stringify({
        mobileNumber: "9555555555",
        role: "CASHIER",
      }),
    });

    expect(res.status).toBe(403);
    const body = await res.json();
    expect(body.error.code).toBe("FORBIDDEN");
  });

  it("Cashier CANNOT manage bank accounts (should return 403 Forbidden)", async () => {
    const res = await app.request(`/api/v1/organizations/${orgId}/accounts`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${cashierToken}`,
      },
      body: JSON.stringify({
        bankName: "HDFC Bank",
        accountHolderName: "RBAC Test Org",
        accountNumber: "123456789012",
        ifscCode: "HDFC0001234",
      }),
    });

    expect(res.status).toBe(403);
  });
});
