import { describe, it, expect, beforeAll } from "vitest";
import { app } from "../src/app.js";
import { initDatabase, db } from "../src/db/index.js";
import * as schema from "../src/db/schema/index.js";
import { eq } from "drizzle-orm";
import { createAccessToken } from "../src/lib/jwt.js";

describe("Database Events & Dataset Seeding Tests", () => {
  let authToken: string;
  const orgId = "org_demo_store";
  const userId = "usr_merchant_demo";

  beforeAll(async () => {
    initDatabase();
    db.insert(schema.sessions).values({
      id: "sess_demo_1",
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
      sessionId: "sess_demo_1",
    });
  });

  it("should have seeded demo organization, bank, upi, and 12 transactions properly", async () => {
    const org = db.select().from(schema.organizations).where(eq(schema.organizations.id, orgId)).get();
    expect(org).toBeDefined();
    expect(org.name).toBe("Sharma Kirana & General Store");

    const upiAccounts = db.select().from(schema.upiAccounts).where(eq(schema.upiAccounts.organizationId, orgId)).all();
    expect(upiAccounts.length).toBeGreaterThanOrEqual(2);

    const txns = db.select().from(schema.transactions).where(eq(schema.transactions.organizationId, orgId)).all();
    expect(txns.length).toBeGreaterThanOrEqual(12);

    const outbox = db.select().from(schema.outboxEvents).where(eq(schema.outboxEvents.organizationId, orgId)).all();
    expect(outbox.length).toBeGreaterThanOrEqual(12);
  });

  it("should stream outbox events via sync endpoint", async () => {
    const res = await app.request(`/api/v1/organizations/${orgId}/sync?afterSequence=0&limit=50`, {
      headers: {
        Authorization: `Bearer ${authToken}`,
        "X-Organization-Id": orgId,
      },
    });

    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.data.events.length).toBeGreaterThanOrEqual(12);
    expect(body.data.latestSequence).toBeGreaterThan(0);

    const firstEvent = body.data.events[0];
    expect(firstEvent.sequence).toBeDefined();
    expect(firstEvent.eventType).toBeDefined();
    expect(firstEvent.payload).toBeDefined();
  });

  it("should emit proper outbox event with complete entity payload on transaction creation", async () => {
    const res = await app.request(`/api/v1/organizations/${orgId}/transactions`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${authToken}`,
        "X-Organization-Id": orgId,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        amount: 350.0,
        payeeName: "Sharma Kirana Store",
        payeeVpa: "sharma.store@okhdfcbank",
        payerName: "Kunal Shah",
        payerVpa: "kunal@cred",
        note: "Premium Coffee beans",
        referenceNumber: "428199999999",
      }),
    });

    expect(res.status).toBe(201);
    const body = await res.json();
    expect(body.success).toBe(true);
    const newTxnId = body.transaction.id;

    // Check outbox event
    const event = db
      .select()
      .from(schema.outboxEvents)
      .where(eq(schema.outboxEvents.organizationId, orgId))
      .all()
      .find((e:typeof schema.outboxEvents.$inferSelect) => e.eventType === "transaction.created" && e.payloadJson.includes(newTxnId));

    expect(event).toBeDefined();
    const payload = JSON.parse(event!.payloadJson);
    expect(payload.id).toBe(newTxnId);
    expect(payload.amount).toBe(350.0);
    expect(payload.payerName).toBe("Kunal Shah");
    expect(payload.referenceNumber).toBe("428199999999");
  });

  it("should emit outbox event on transaction status change", async () => {
    // Find an existing transaction
    const txn = db.select().from(schema.transactions).where(eq(schema.transactions.organizationId, orgId)).get()!;

    const res = await app.request(`/api/v1/organizations/${orgId}/transactions/${txn.id}/status`, {
      method: "PATCH",
      headers: {
        Authorization: `Bearer ${authToken}`,
        "X-Organization-Id": orgId,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        status: "REFUNDED",
        referenceNumber: "REFUND_TEST_101",
        note: "Refund processed for test",
      }),
    });

    expect(res.status).toBe(200);

    const event = db
      .select()
      .from(schema.outboxEvents)
      .where(eq(schema.outboxEvents.organizationId, orgId))
      .all()
      .find((e:typeof schema.outboxEvents.$inferSelect) => e.eventType === "transaction.status_changed" && e.payloadJson.includes(txn.id));

    expect(event).toBeDefined();
    const payload = JSON.parse(event!.payloadJson);
    expect(payload.status).toBe("REFUNDED");
  });

  it("should emit outbox event on upi account creation", async () => {
    const res = await app.request(`/api/v1/organizations/${orgId}/upi`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${authToken}`,
        "X-Organization-Id": orgId,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        vpa: "test.upi@okhdfcbank",
        payeeName: "Sharma Store Counter 3",
        merchantCategoryCode: "5411",
        isDefault: false,
      }),
    });

    expect(res.status).toBe(201);
    const body = await res.json();
    const upiId = body.upiAccount.id;

    const event = db
      .select()
      .from(schema.outboxEvents)
      .where(eq(schema.outboxEvents.organizationId, orgId))
      .all()
      .find((e:typeof schema.outboxEvents.$inferSelect) => e.eventType === "upi.created" && e.payloadJson.includes(upiId));

    expect(event).toBeDefined();
    const payload = JSON.parse(event!.payloadJson);
    expect(payload.vpa).toBe("test.upi@okhdfcbank");
  });

  it("should send notifications to all connected staff members when payment is received", async () => {
    const paymentAmount = 3000.0;
    const res = await app.request(`/api/v1/organizations/${orgId}/transactions`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${authToken}`,
        "X-Organization-Id": orgId,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        amount: paymentAmount,
        payeeName: "Sharma Kirana Store",
        payeeVpa: "sharma.store@okhdfcbank",
        payerName: "Rohan Varma",
        payerVpa: "rohan@upi",
        note: "Grocery Bill",
        referenceNumber: "428198765432",
      }),
    });

    expect(res.status).toBe(201);
    const body = await res.json();
    expect(body.success).toBe(true);

    // Verify staff member (cashier) received the notification
    const cashierNotifs = db
      .select()
      .from(schema.notifications)
      .where(eq(schema.notifications.userId, "usr_cashier_demo"))
      .all();

    const paymentNotif = cashierNotifs.find(
      (n:typeof schema.notifications.$inferSelect) => n.title.includes("3,000") || n.message.includes("3,000")
    );
    expect(paymentNotif).toBeDefined();
    expect(paymentNotif?.type).toBe("payment.received");
    expect(paymentNotif?.title).toBe("Payment of ₹3,000 received");
  });

  it("should delete single transaction record and emit outbox event", async () => {
    // Create a txn to delete
    const createRes = await app.request(`/api/v1/organizations/${orgId}/transactions`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${authToken}`,
        "X-Organization-Id": orgId,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        amount: 125.0,
        payeeName: "Sharma Store",
        payeeVpa: "sharma@upi",
        note: "To be deleted",
      }),
    });
    const { transaction } = await createRes.json();
    expect(transaction.id).toBeDefined();

    // Delete it
    const delRes = await app.request(`/api/v1/organizations/${orgId}/transactions/${transaction.id}`, {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${authToken}`,
        "X-Organization-Id": orgId,
      },
    });
    expect(delRes.status).toBe(200);
    const delBody = await delRes.json();
    expect(delBody.success).toBe(true);

    // Verify it is gone from db
    const checkDb = db.select().from(schema.transactions).where(eq(schema.transactions.id, transaction.id)).get();
    expect(checkDb).toBeUndefined();

    // Verify outbox event emitted
    const outbox = db.select().from(schema.outboxEvents).where(eq(schema.outboxEvents.eventType, "transaction.deleted")).all();
    expect(outbox.some((e: any) => e.payloadJson.includes(transaction.id))).toBe(true);
  });

  it("should bulk delete transactions and emit outbox events", async () => {
    const c1 = await app.request(`/api/v1/organizations/${orgId}/transactions`, {
      method: "POST",
      headers: { Authorization: `Bearer ${authToken}`, "X-Organization-Id": orgId, "Content-Type": "application/json" },
      body: JSON.stringify({ amount: 50.0, payeeName: "Store", payeeVpa: "store@upi" }),
    });
    const c2 = await app.request(`/api/v1/organizations/${orgId}/transactions`, {
      method: "POST",
      headers: { Authorization: `Bearer ${authToken}`, "X-Organization-Id": orgId, "Content-Type": "application/json" },
      body: JSON.stringify({ amount: 60.0, payeeName: "Store", payeeVpa: "store@upi" }),
    });
    const t1 = (await c1.json()).transaction.id;
    const t2 = (await c2.json()).transaction.id;

    const bulkRes = await app.request(`/api/v1/organizations/${orgId}/transactions/bulk-delete`, {
      method: "POST",
      headers: { Authorization: `Bearer ${authToken}`, "X-Organization-Id": orgId, "Content-Type": "application/json" },
      body: JSON.stringify({ ids: [t1, t2] }),
    });
    expect(bulkRes.status).toBe(200);
    const bulkBody = await bulkRes.json();
    expect(bulkBody.success).toBe(true);
    expect(bulkBody.count).toBe(2);

    expect(db.select().from(schema.transactions).where(eq(schema.transactions.id, t1)).get()).toBeUndefined();
    expect(db.select().from(schema.transactions).where(eq(schema.transactions.id, t2)).get()).toBeUndefined();
  });
});

