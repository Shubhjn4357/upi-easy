import { describe, it, expect, beforeAll } from "vitest";
import { app } from "../src/app.js";
import { initDatabase } from "../src/db/index.js";

beforeAll(() => {
  initDatabase();
});

describe("Multi-Tenant Isolation Security Tests", () => {
  let tokenA: string;
  let tokenB: string;
  let orgAId: string;
  let orgBId: string;

  beforeAll(async () => {
    // Setup User A
    const verA = await app.request("/api/v1/auth/google", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        idToken: "mock:tenantA@gmail.com:google_sub_tenantA",
        email: "tenantA@gmail.com",
        fullName: "Tenant User A",
      }),
    });
    tokenA = (await verA.json()).tokens.accessToken;

    // Create Org A
    const createOrgA = await app.request("/api/v1/organizations", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${tokenA}`,
      },
      body: JSON.stringify({ name: "Business A Store" }),
    });
    orgAId = (await createOrgA.json()).organization.id;

    // Setup User B
    const verB = await app.request("/api/v1/auth/google", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        idToken: "mock:tenantB@gmail.com:google_sub_tenantB",
        email: "tenantB@gmail.com",
        fullName: "Tenant User B",
      }),
    });
    tokenB = (await verB.json()).tokens.accessToken;

    // Create Org B
    const createOrgB = await app.request("/api/v1/organizations", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${tokenB}`,
      },
      body: JSON.stringify({ name: "Business B Enterprises" }),
    });
    orgBId = (await createOrgB.json()).organization.id;
  });

  it("User A must be rejected with 403 when attempting to access Org B accounts", async () => {
    const res = await app.request(`/api/v1/organizations/${orgBId}/accounts`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${tokenA}`,
      },
    });

    expect(res.status).toBe(403);
    const body = await res.json();
    expect(body.error.code).toBe("FORBIDDEN");
  });

  it("User A must be rejected with 403 when attempting to access Org B transactions", async () => {
    const res = await app.request(`/api/v1/organizations/${orgBId}/transactions`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${tokenA}`,
      },
    });

    expect(res.status).toBe(403);
  });

  it("User A must be rejected with 403 when attempting to access Org B staff", async () => {
    const res = await app.request(`/api/v1/organizations/${orgBId}/staff`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${tokenA}`,
      },
    });

    expect(res.status).toBe(403);
  });

  it("User A must be rejected with 403 when attempting to access Org B UPI accounts", async () => {
    const res = await app.request(`/api/v1/organizations/${orgBId}/upi`, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${tokenA}`,
      },
    });

    expect(res.status).toBe(403);
  });
});
