import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { app } from "../src/app.js";
import { initDatabase, db } from "../src/db/index.js";
import * as schema from "../src/db/schema/index.js";
import { eq, and } from "drizzle-orm";
import { createAccessToken } from "../src/lib/jwt.js";

describe("UPI ID Edit & Management API Tests", () => {
  let authToken: string;
  const orgId = "org_demo_store";
  const userId = "usr_merchant_demo";

  beforeAll(async () => {
    initDatabase();
    await db.delete(schema.upiAccounts).where(eq(schema.upiAccounts.vpa, "oldshop@okhdfcbank")).run();
    await db.delete(schema.upiAccounts).where(eq(schema.upiAccounts.vpa, "newshop@okaxis")).run();

    // Ensure session
    db.insert(schema.sessions).values({
      id: "sess_edit_upi_test",
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
      sessionId: "sess_edit_upi_test",
    });
  });

  it("should create a UPI account and then edit the UPI ID and Payee Name", async () => {
    // 1. Create UPI Account
    const createRes = await app.request(`/api/v1/organizations/${orgId}/upi`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${authToken}`,
        "X-Organization-Id": orgId,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        vpa: "oldshop@okhdfcbank",
        payeeName: "Old Shop Counter",
        isDefault: true,
      }),
    });

    expect(createRes.status).toBe(201);
    const createBody = await createRes.json();
    const upiId = createBody.upiAccount.id;
    expect(createBody.upiAccount.vpa).toBe("oldshop@okhdfcbank");

    // 2. Edit UPI Account via PUT
    const editRes = await app.request(`/api/v1/organizations/${orgId}/upi/${upiId}`, {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${authToken}`,
        "X-Organization-Id": orgId,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        vpa: "newshop@okaxis",
        payeeName: "New Upgraded Store",
      }),
    });

    expect(editRes.status).toBe(200);
    const editBody = await editRes.json();
    expect(editBody.success).toBe(true);
    expect(editBody.upiAccount.vpa).toBe("newshop@okaxis");
    expect(editBody.upiAccount.payeeName).toBe("New Upgraded Store");

    // 3. Verify DB record updated
    const updatedUpi = db
      .select()
      .from(schema.upiAccounts)
      .where(eq(schema.upiAccounts.id, upiId))
      .get();
    expect(updatedUpi).toBeDefined();
    expect(updatedUpi.vpa).toBe("newshop@okaxis");
    expect(updatedUpi.payeeName).toBe("New Upgraded Store");

    // 4. Verify associated QR code payload updated
    const associatedQr = db
      .select()
      .from(schema.qrCodes)
      .where(eq(schema.qrCodes.upiAccountId, upiId))
      .get();
    expect(associatedQr).toBeDefined();
    expect(associatedQr.qrPayload).toContain("pa=newshop%40okaxis");
    expect(associatedQr.qrPayload).toContain("pn=New+Upgraded+Store");

    // 5. Verify audit log entry created
    const audit = db
      .select()
      .from(schema.auditLogs)
      .where(
        and(
          eq(schema.auditLogs.organizationId, orgId),
          eq(schema.auditLogs.action, "upi.updated"),
          eq(schema.auditLogs.resourceId, upiId)
        )
      )
      .get();
    expect(audit).toBeDefined();
  });

  it("should reject invalid VPA formats on edit", async () => {
    // Try to update with an invalid VPA
    const res = await app.request(`/api/v1/organizations/${orgId}/upi/some_id`, {
      method: "PATCH",
      headers: {
        Authorization: `Bearer ${authToken}`,
        "X-Organization-Id": orgId,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        vpa: "invalid_vpa_without_at",
      }),
    });

    expect(res.status).toBe(400);
  });

  afterAll(async () => {
    await db.delete(schema.upiAccounts).where(eq(schema.upiAccounts.vpa, "newshop@okaxis")).run();
    await db.delete(schema.upiAccounts).where(eq(schema.upiAccounts.vpa, "oldshop@okhdfcbank")).run();
  });
});
