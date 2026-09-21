import { describe, it, expect, beforeEach } from "vitest";
import { app } from "../src/app.js";
import { db } from "../src/db/index.js";
import * as schema from "../src/db/schema/index.js";
import { generateId } from "../src/lib/crypto.js";
import { createAccessToken } from "../src/lib/jwt.js";
import { normalizeIndianMobileNumber, get10DigitMobile } from "../src/lib/phone.js";

describe("Phone Normalization", () => {
  it("normalizes various Indian phone formats to canonical +91 format", () => {
    expect(normalizeIndianMobileNumber("9876543210")).toBe("+919876543210");
    expect(normalizeIndianMobileNumber("+919876543210")).toBe("+919876543210");
    expect(normalizeIndianMobileNumber("919876543210")).toBe("+919876543210");
    expect(normalizeIndianMobileNumber("09876543210")).toBe("+919876543210");
    expect(normalizeIndianMobileNumber(" +91 98765-43210 ")).toBe("+919876543210");
    expect(get10DigitMobile("+919876543210")).toBe("9876543210");
  });

  it("throws on invalid mobile numbers", () => {
    expect(() => normalizeIndianMobileNumber("12345")).toThrow();
    expect(() => normalizeIndianMobileNumber("1234567890")).toThrow(); // Starts with 1
  });
});

describe("Multi-Firm Staff Invitations, Devices & Sync", () => {
  let ownerUserId: string;
  let ownerToken: string;
  let staffUserId: string;
  let staffToken: string;
  let otherUserId: string;
  let otherToken: string;
  let orgId: string;

  beforeEach(async () => {
    const now = new Date();
    await db.delete(schema.organizationInvites).run();

    // 1. Create Roles
    const standardRoles = [
      { id: "role_owner", name: "OWNER", description: "Owner", isSystem: true },
      { id: "role_manager", name: "MANAGER", description: "Manager", isSystem: true },
      { id: "role_cashier", name: "CASHIER", description: "Cashier", isSystem: true },
    ];
    for (const r of standardRoles) {
      await db.insert(schema.roles).values(r).onConflictDoNothing().run();
    }

    // 2. Create Permissions
    const perms = [
      { id: "perm_staff_manage", name: "staff.manage", description: "Manage staff", category: "STAFF" },
      { id: "perm_staff_read", name: "staff.read", description: "Read staff", category: "STAFF" },
    ];
    for (const p of perms) {
      await db.insert(schema.permissions).values(p).onConflictDoNothing().run();
      await db.insert(schema.rolePermissions).values({
        id: generateId("rp"),
        roleId: "role_owner",
        permissionId: p.id,
      }).onConflictDoNothing().run();
    }

    // 3. Create Owner User
    ownerUserId = generateId("usr");
    await db.insert(schema.users).values({
      id: ownerUserId,
      fullName: "Owner User",
      mobileNumber: "+919876500001",
      email: "owner@test.com",
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    }).run();

    ownerToken = await createAccessToken({
      sub: ownerUserId,
      mobileNumber: "+919876500001",
      sessionId: "sess_owner",
    });
    await db.insert(schema.sessions).values({
      id: "sess_owner",
      userId: ownerUserId,
      expiresAt: new Date(Date.now() + 86400000),
      isRevoked: false,
      createdAt: now,
      updatedAt: now,
    }).onConflictDoNothing().run();

    // 4. Create Organization
    orgId = generateId("org");
    await db.insert(schema.organizations).values({
      id: orgId,
      name: "Apex Retailers",
      ownerId: ownerUserId,
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    }).run();

    await db.insert(schema.organizationMembers).values({
      id: generateId("mem"),
      organizationId: orgId,
      userId: ownerUserId,
      roleId: "role_owner",
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    }).run();

    // 5. Create Staff User (Recipient)
    staffUserId = generateId("usr");
    await db.insert(schema.users).values({
      id: staffUserId,
      fullName: "Rahul Cashier",
      mobileNumber: "+919876500002",
      email: "rahul@test.com",
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    }).run();

    staffToken = await createAccessToken({
      sub: staffUserId,
      mobileNumber: "+919876500002",
      sessionId: "sess_staff",
    });
    await db.insert(schema.sessions).values({
      id: "sess_staff",
      userId: staffUserId,
      expiresAt: new Date(Date.now() + 86400000),
      isRevoked: false,
      createdAt: now,
      updatedAt: now,
    }).onConflictDoNothing().run();

    // 6. Create Unrelated User
    otherUserId = generateId("usr");
    await db.insert(schema.users).values({
      id: otherUserId,
      fullName: "Other User",
      mobileNumber: "+919876500003",
      email: "other@test.com",
      status: "ACTIVE",
      createdAt: now,
      updatedAt: now,
    }).run();

    otherToken = await createAccessToken({
      sub: otherUserId,
      mobileNumber: "+919876500003",
      sessionId: "sess_other",
    });
    await db.insert(schema.sessions).values({
      id: "sess_other",
      userId: otherUserId,
      expiresAt: new Date(Date.now() + 86400000),
      isRevoked: false,
      createdAt: now,
      updatedAt: now,
    }).onConflictDoNothing().run();
  });

  it("prevents owner from inviting self", async () => {
    const res = await app.request(`/api/v1/organizations/${orgId}/invites`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${ownerToken}`,
        "Content-Type": "application/json",
        "X-Organization-Id": orgId,
      },
      body: JSON.stringify({
        mobileNumber: "9876500001", // Owner's mobile
        role: "CASHIER",
      }),
    });

    expect(res.status).toBe(400);
    const json = await res.json();
    expect(json.success).toBe(false);
    expect(json.error.code).toBe("CANNOT_INVITE_SELF");
  });

  it("creates invitation for an existing user and allows recipient to accept", async () => {
    // 1. Send invite
    const inviteRes = await app.request(`/api/v1/organizations/${orgId}/invites`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${ownerToken}`,
        "Content-Type": "application/json",
        "X-Organization-Id": orgId,
      },
      body: JSON.stringify({
        mobileNumber: "98765 00002", // Staff mobile formatted with spaces
        name: "Rahul",
        role: "CASHIER",
      }),
    });

    expect(inviteRes.status).toBe(200);
    const inviteJson = await inviteRes.json();
    expect(inviteJson.success).toBe(true);
    expect(inviteJson.invite.status).toBe("PENDING");
    expect(inviteJson.invite.organizationName).toBe("Apex Retailers");
    const inviteId = inviteJson.invite.id;

    // 2. Prevent duplicate pending invite
    const dupRes = await app.request(`/api/v1/organizations/${orgId}/invites`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${ownerToken}`,
        "Content-Type": "application/json",
        "X-Organization-Id": orgId,
      },
      body: JSON.stringify({
        mobileNumber: "+919876500002",
        role: "CASHIER",
      }),
    });
    expect(dupRes.status).toBe(409);

    // 3. Check recipient pending invites list
    const myInvitesRes = await app.request("/api/v1/me/invitations", {
      headers: {
        Authorization: `Bearer ${staffToken}`,
      },
    });
    expect(myInvitesRes.status).toBe(200);
    const myInvitesJson = await myInvitesRes.json();
    expect(myInvitesJson.success).toBe(true);
    expect(myInvitesJson.invitations.length).toBe(1);
    expect(myInvitesJson.invitations[0].id).toBe(inviteId);

    // 4. Other user cannot accept this invitation
    const forbidRes = await app.request(`/api/v1/invitations/${inviteId}/accept`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${otherToken}`,
      },
    });
    expect(forbidRes.status).toBe(403);

    // 5. Authorized recipient accepts invitation
    const acceptRes = await app.request(`/api/v1/invitations/${inviteId}/accept`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${staffToken}`,
      },
    });
    expect(acceptRes.status).toBe(200);
    const acceptJson = await acceptRes.json();
    expect(acceptJson.success).toBe(true);
    expect(acceptJson.organization.id).toBe(orgId);
    expect(acceptJson.organization.role).toBe("CASHIER");

    // 6. Organization now appears in recipient's /api/v1/me organizations
    const meRes = await app.request("/api/v1/me", {
      headers: {
        Authorization: `Bearer ${staffToken}`,
      },
    });
    expect(meRes.status).toBe(200);
    const meJson = await meRes.json();
    expect(meJson.organizations.some((o: any) => o.organizationId === orgId)).toBe(true);

    // 7. Accepting again fails with conflict
    const acceptAgain = await app.request(`/api/v1/invitations/${inviteId}/accept`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${staffToken}`,
      },
    });
    expect(acceptAgain.status).toBe(409);
  });

  it("registers devices and supports multi-organization sync", async () => {
    // 1. Register Device A
    const devRes = await app.request("/api/v1/devices/register", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${staffToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        deviceId: "android_pixel_8",
        platform: "ANDROID",
        deviceModel: "Pixel 8",
        osVersion: "14",
        appVersion: "1.0.0",
        fcmToken: "mock_fcm_token_pixel",
      }),
    });
    expect(devRes.status).toBe(200);
    const devJson = await devRes.json();
    expect(devJson.success).toBe(true);

    // 2. Perform Multi-Organization Sync
    const syncRes = await app.request("/api/v1/sync", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${ownerToken}`,
        "Content-Type": "application/json",
        "X-Device-Id": "android_owner_phone",
      },
      body: JSON.stringify({
        organizations: [
          { organizationId: orgId, cursor: 0 },
        ],
      }),
    });
    expect(syncRes.status).toBe(200);
    const syncJson = await syncRes.json();
    expect(syncJson.success).toBe(true);
    expect(syncJson.organizations.length).toBe(1);
    expect(syncJson.organizations[0].organizationId).toBe(orgId);

    // 3. Unregister device
    const unregRes = await app.request("/api/v1/devices/unregister", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${staffToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        deviceId: "android_pixel_8",
      }),
    });
    expect(unregRes.status).toBe(200);
  });
});
