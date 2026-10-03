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
    expect(body.data).toHaveLength(4);
    expect(body.data.map((d: any) => d.id)).toEqual(["phonepe", "google_pay", "bhim", "paytm"]);
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

  it("should create BHIM and Paytm payment accounts and ingest their observed payment events", async () => {
    // 1. Create BHIM payment account
    const bhimAccRes = await app.request(`/api/v1/organizations/${orgId}/payment-accounts`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${authToken}`,
        "X-Organization-Id": orgId,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        label: "Counter 2 BHIM",
        upiId: "bhim.merchant@upi",
        paymentAppId: "bhim",
        paymentAppPackage: "in.org.npci.upiapp",
        detectionEnabled: true,
        notificationAccessRequired: true,
      }),
    });
    expect(bhimAccRes.status).toBe(201);
    const bhimAcc = await bhimAccRes.json();
    expect(bhimAcc.data.paymentAppId).toBe("bhim");
    expect(bhimAcc.data.paymentAppPackage).toBe("in.org.npci.upiapp");

    // 2. Create Paytm payment account
    const paytmAccRes = await app.request(`/api/v1/organizations/${orgId}/payment-accounts`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${authToken}`,
        "X-Organization-Id": orgId,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        label: "Counter 3 Paytm",
        upiId: "paytm.merchant@paytm",
        paymentAppId: "paytm",
        paymentAppPackage: "net.one97.paytm",
        detectionEnabled: true,
        notificationAccessRequired: true,
      }),
    });
    expect(paytmAccRes.status).toBe(201);
    const paytmAcc = await paytmAccRes.json();
    expect(paytmAcc.data.paymentAppId).toBe("paytm");
    expect(paytmAcc.data.paymentAppPackage).toBe("net.one97.paytm");

    // 3. Ingest BHIM observed payment event
    const bhimEventRes = await app.request(`/api/v1/organizations/${orgId}/payment-events/observed`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${authToken}`,
        "X-Organization-Id": orgId,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        clientEventId: "evt_local_bhim_1",
        source: {
          type: "NOTIFICATION_BHIM",
          packageName: "in.org.npci.upiapp",
        },
        paymentAccountId: bhimAcc.data.id,
        amountMinor: 35000, // Rs 350.00
        currency: "INR",
        direction: "RECEIVED",
        payerName: "Kavita Sharma",
        payerVpa: "kavita@upi",
        reference: "998877665544",
        observedAt: new Date().toISOString(),
        verificationStatus: "OBSERVED",
        matchStatus: "MATCHED",
        fingerprint: "fp_test_bhim_001_unique_hash_987",
      }),
    });
    expect(bhimEventRes.status).toBe(201);
    const bhimBody = await bhimEventRes.json();
    expect(bhimBody.accepted).toBe(true);
    expect(bhimBody.status).toBe("OBSERVED");

    const bhimTxn = db
      .select()
      .from(schema.transactions)
      .where(eq(schema.transactions.id, bhimBody.transactionId))
      .get();
    expect(bhimTxn?.status).toBe("UNKNOWN");
    expect(bhimTxn?.verificationStatus).toBe("OBSERVED");
    expect(bhimTxn?.eventSource).toBe("NOTIFICATION_BHIM");
    expect(bhimTxn?.amount).toBe(350.0);

    // 4. Ingest Paytm observed payment event
    const paytmEventRes = await app.request(`/api/v1/organizations/${orgId}/payment-events/observed`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${authToken}`,
        "X-Organization-Id": orgId,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        clientEventId: "evt_local_paytm_1",
        source: {
          type: "NOTIFICATION_PAYTM",
          packageName: "net.one97.paytm",
        },
        paymentAccountId: paytmAcc.data.id,
        amountMinor: 120000, // Rs 1200.00
        currency: "INR",
        direction: "RECEIVED",
        payerName: "Sanjay Kumar",
        payerVpa: "sanjay@paytm",
        reference: "554433221100",
        observedAt: new Date().toISOString(),
        verificationStatus: "OBSERVED",
        matchStatus: "MATCHED",
        fingerprint: "fp_test_paytm_001_unique_hash_123",
      }),
    });
    expect(paytmEventRes.status).toBe(201);
    const paytmBody = await paytmEventRes.json();
    expect(paytmBody.accepted).toBe(true);
    expect(paytmBody.status).toBe("OBSERVED");

    const paytmTxn = db
      .select()
      .from(schema.transactions)
      .where(eq(schema.transactions.id, paytmBody.transactionId))
      .get();
    expect(paytmTxn?.status).toBe("UNKNOWN");
    expect(paytmTxn?.verificationStatus).toBe("OBSERVED");
    expect(paytmTxn?.eventSource).toBe("NOTIFICATION_PAYTM");
    expect(paytmTxn?.amount).toBe(1200.0);
  });
});
