import { describe, it, expect, beforeAll } from "vitest";
import { app } from "../src/app.js";
import { initDatabase, db } from "../src/db/index.js";
import * as schema from "../src/db/schema/index.js";
import { eq, and } from "drizzle-orm";
import { createAccessToken } from "../src/lib/jwt.js";

describe("Payment Accounts & Observed Events Integration Tests", () => {
  let authToken: string;
  const orgId = "org_demo_store";
  const userId = "usr_merchant_demo";

  beforeAll(async () => {
    initDatabase();
    await db.delete(schema.observedPaymentEvents).run();
    await db.delete(schema.paymentAccounts).run();

    db.insert(schema.sessions).values({
      id: "sess_demo_payment_tests",
      userId,
      deviceId: null,
      expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
      isRevoked: false,
      createdAt: new Date(),
      updatedAt: new Date(),
    }).onConflictDoNothing().run();

    authToken = await createAccessToken({
      sub: userId,
      mobileNumber: "9876543210",
      sessionId: "sess_demo_payment_tests",
    });
  });

  it("should return supported payment apps for detection", async () => {
    const res = await app.request("/api/v1/payment-apps/supported");
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.data).toHaveLength(2);
    expect(body.data.map((d: any) => d.id)).toEqual(["phonepe", "google_pay"]);
  });

  it("should create and list a payment account", async () => {
    const createRes = await app.request(`/api/v1/organizations/${orgId}/payment-accounts`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${authToken}`,
        "X-Organization-Id": orgId,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        label: "Counter 1 PhonePe",
        upiId: "sharma.store@ybl",
        paymentAppId: "phonepe",
        paymentAppPackage: "com.phonepe.app",
        detectionEnabled: true,
        notificationAccessRequired: true,
      }),
    });

    expect(createRes.status).toBe(201);
    const createBody = await createRes.json();
    expect(createBody.success).toBe(true);
    expect(createBody.data.label).toBe("Counter 1 PhonePe");
    expect(createBody.data.paymentAppPackage).toBe("com.phonepe.app");

    const paId = createBody.data.id;

    // List accounts
    const listRes = await app.request(`/api/v1/organizations/${orgId}/payment-accounts`, {
      headers: {
        Authorization: `Bearer ${authToken}`,
        "X-Organization-Id": orgId,
      },
    });

    expect(listRes.status).toBe(200);
    const listBody = await listRes.json();
    expect(listBody.success).toBe(true);
    const found = listBody.data.find((a: any) => a.id === paId);
    expect(found).toBeDefined();
    expect(found.upiId).toBe("sharma.store@ybl");
  });

  it("should ingest an observed payment event and create an UNKNOWN/OBSERVED transaction", async () => {
    const fingerprint = "fp_test_phonepe_notification_001_abc12345";
    const postRes = await app.request(`/api/v1/organizations/${orgId}/payment-events/observed`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${authToken}`,
        "X-Organization-Id": orgId,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        clientEventId: "evt_local_test_1",
        source: {
          type: "NOTIFICATION_PHONEPE",
          packageName: "com.phonepe.app",
        },
        paymentAccountId: null,
        amountMinor: 50000, // Rs 500.00
        currency: "INR",
        direction: "RECEIVED",
        payerName: "Rohan Verma",
        payerVpa: "rohan@ybl",
        reference: "987654321098",
        observedAt: new Date().toISOString(),
        verificationStatus: "OBSERVED",
        matchStatus: "MATCHED",
        fingerprint,
      }),
    });

    expect(postRes.status).toBe(201);
    const body = await postRes.json();
    expect(body.accepted).toBe(true);
    expect(body.status).toBe("OBSERVED");
    expect(body.eventId).toBeDefined();
    expect(body.transactionId).toBeDefined();

    // Verify transaction record semantics in DB
    const txn = db
      .select()
      .from(schema.transactions)
      .where(eq(schema.transactions.id, body.transactionId))
      .get();

    expect(txn).toBeDefined();
    // Rule check: MUST NOT be SUCCESS!
    expect(txn.status).toBe("UNKNOWN");
    expect(txn.verificationStatus).toBe("OBSERVED");
    expect(txn.eventSource).toBe("NOTIFICATION_PHONEPE");
    expect(txn.amount).toBe(500.0);
    expect(txn.payerName).toBe("Rohan Verma");
    expect(txn.referenceNumber).toBe("987654321098");

    // Verify outbox event created
    const outbox = db
      .select()
      .from(schema.outboxEvents)
      .where(
        and(
          eq(schema.outboxEvents.organizationId, orgId),
          eq(schema.outboxEvents.eventType, "PAYMENT_OBSERVED")
        )
      )
      .all();

    expect(outbox.length).toBeGreaterThanOrEqual(1);
    const lastOutbox = outbox[outbox.length - 1];
    const payload = JSON.parse(lastOutbox.payloadJson);
    expect(payload.type).toBe("PAYMENT_OBSERVED");
    expect(payload.amountMinor).toBe(50000);
    expect(payload.source).toBe("PHONEPE");
  });

  it("should handle event deduplication on repeated fingerprint idempotently", async () => {
    const fingerprint = "fp_test_duplicate_detection_002_xyz98765";
    const payload = {
      clientEventId: "evt_local_test_2",
      source: {
        type: "NOTIFICATION_GPAY",
        packageName: "com.google.android.apps.nbu.paisa.user",
      },
      amountMinor: 25000, // Rs 250.00
      currency: "INR",
      direction: "RECEIVED",
      payerName: "Amit Patel",
      payerVpa: "amit@okhdfcbank",
      reference: "112233445566",
      observedAt: new Date().toISOString(),
      verificationStatus: "OBSERVED",
      matchStatus: "MATCHED",
      fingerprint,
    };

    // First ingestion
    const res1 = await app.request(`/api/v1/organizations/${orgId}/payment-events/observed`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${authToken}`,
        "X-Organization-Id": orgId,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });
    expect(res1.status).toBe(201);
    const body1 = await res1.json();
    expect(body1.accepted).toBe(true);

    // Second ingestion with same fingerprint
    const res2 = await app.request(`/api/v1/organizations/${orgId}/payment-events/observed`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${authToken}`,
        "X-Organization-Id": orgId,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });
    expect(res2.status).toBe(200);
    const body2 = await res2.json();
    expect(body2.accepted).toBe(true);
    expect(body2.eventId).toBe(body1.eventId);
    expect(body2.duplicate).toBe(true);
  });
});
