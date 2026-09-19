import { describe, it, expect, beforeAll } from "vitest";
import { app } from "../src/app.js";
import { initDatabase } from "../src/db/index.js";
import { signHmac } from "../src/lib/crypto.js";
import { config } from "../src/config/index.js";

beforeAll(() => {
  initDatabase();
});

describe("Webhook & Reconciliation Engine Tests", () => {
  let token: string;
  let orgId: string;
  let txnId: string;
  let testRrn: string;

  beforeAll(async () => {
    testRrn = `rrn_${Date.now()}_${Math.random().toString(36).substring(2, 8)}`;
    const ver = await app.request("/api/v1/auth/google", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        idToken: "mock:recon.test@gmail.com:google_sub_recon",
        email: "recon.test@gmail.com",
        fullName: "Recon Tester",
      }),
    });
    token = (await ver.json()).tokens.accessToken;

    const createOrg = await app.request("/api/v1/organizations", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ name: "Recon Test Org" }),
    });
    orgId = (await createOrg.json()).organization.id;

    // Create a pending transaction
    const createTxn = await app.request(`/api/v1/organizations/${orgId}/transactions`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        amount: 500.0,
        payeeName: "Recon Store",
        payeeVpa: "store@axis",
        referenceNumber: testRrn,
      }),
    });
    txnId = (await createTxn.json()).transaction.id;
  });

  it("should reconcile pending transaction to SUCCESS upon receiving signed provider webhook", async () => {
    const providerTxnId = `prov_${Date.now()}_${Math.random().toString(36).substring(2, 8)}`;
    const webhookPayload = JSON.stringify({
      providerTransactionId: providerTxnId,
      referenceNumber: testRrn,
      amount: 500.0,
      status: "SUCCESS",
    });

    const signature = signHmac(config.WEBHOOK_SECRET, webhookPayload);

    const webhookRes = await app.request("/api/v1/webhooks/hdfc", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Webhook-Signature": signature,
      },
      body: webhookPayload,
    });

    expect(webhookRes.status).toBe(200);
    const webhookBody = await webhookRes.json();
    expect(webhookBody.success).toBe(true);

    // Verify transaction is now SUCCESS
    const getTxn = await app.request(`/api/v1/organizations/${orgId}/transactions/${txnId}`, {
      method: "GET",
      headers: { Authorization: `Bearer ${token}` },
    });

    const txnBody = await getTxn.json();
    expect(txnBody.transaction.status).toBe("SUCCESS");
    expect(txnBody.transaction.events.length).toBeGreaterThanOrEqual(2);
  });

  it("delta sync should return the reconciled event stream", async () => {
    const syncRes = await app.request(`/api/v1/organizations/${orgId}/sync?afterSequence=0`, {
      method: "GET",
      headers: { Authorization: `Bearer ${token}` },
    });

    expect(syncRes.status).toBe(200);
    const syncBody = await syncRes.json();
    expect(syncBody.data.events.length).toBeGreaterThanOrEqual(2);
    expect(syncBody.data.latestSequence).toBeGreaterThan(0);
  });
});
