import { describe, it, expect, beforeAll } from "vitest";
import { app } from "../src/app.js";
import { initDatabase } from "../src/db/index.js";

beforeAll(() => {
  initDatabase();
});

describe("Idempotency Engine Tests", () => {
  let token: string;
  let orgId: string;

  beforeAll(async () => {
    const ver = await app.request("/api/v1/auth/google", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        idToken: "mock:idempotency.test@gmail.com:google_sub_idempotency",
        email: "idempotency.test@gmail.com",
        fullName: "Idempotency Tester",
      }),
    });
    token = (await ver.json()).tokens.accessToken;

    const createOrg = await app.request("/api/v1/organizations", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ name: "Idempotency Org" }),
    });
    orgId = (await createOrg.json()).organization.id;
  });

  it("should return cached response and prevent duplicate ledger entries for identical Idempotency-Key", async () => {
    const idempotencyKey = `idem_${Date.now()}_${Math.random().toString(36).substring(2, 8)}`;
    const payload = {
      amount: 1500.0,
      direction: "RECEIVED",
      payeeName: "Test Store",
      payeeVpa: "store@icici",
      note: "Invoice #1001",
    };

    // First request
    const res1 = await app.request(`/api/v1/organizations/${orgId}/transactions`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
        "Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify(payload),
    });

    expect(res1.status).toBe(201);
    const body1 = await res1.json();
    expect(body1.success).toBe(true);
    expect(body1.transaction.id).toBeDefined();

    // Second request with exact same Idempotency-Key
    const res2 = await app.request(`/api/v1/organizations/${orgId}/transactions`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
        "Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify(payload),
    });

    expect(res2.headers.get("x-idempotent-replayed")).toBe("true");
    const body2 = await res2.json();
    expect(body2.transaction.id).toBe(body1.transaction.id);

    // Verify transactions count in database remains 1
    const listRes = await app.request(`/api/v1/organizations/${orgId}/transactions`, {
      method: "GET",
      headers: { Authorization: `Bearer ${token}` },
    });
    const listBody = await listRes.json();
    expect(listBody.data.length).toBe(1);
  });
});
