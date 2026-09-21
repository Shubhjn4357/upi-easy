import { describe, it, expect, beforeAll } from "vitest";
import { app } from "../src/app.js";
import { initDatabase } from "../src/db/index.js";

describe("Admin & Database Table Manager API Tests", () => {
  beforeAll(async () => {
    initDatabase();
  });

  it("should return detailed telemetry from /api/v1/admin/health", async () => {
    const res = await app.request("/api/v1/admin/health");
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.status).toBe("healthy");
    expect(body.service).toBe("upi-easy-api");
    expect(body.uptimeSeconds).toBeGreaterThanOrEqual(0);
    expect(body.memory).toHaveProperty("rssMb");
    expect(body.tables).toHaveProperty("users");
    expect(body.tables).toHaveProperty("organizations");
    expect(body.tables).toHaveProperty("transactions");
  });

  it("should list all accessible tables with schema metadata", async () => {
    const res = await app.request("/api/v1/admin/tables");
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.tables)).toBe(true);

    const userTable = body.tables.find((t: any) => t.name === "users");
    expect(userTable).toBeDefined();
    expect(userTable.columns.length).toBeGreaterThan(0);
  });

  it("should query paginated rows from a table and reject illegal table names", async () => {
    const validRes = await app.request("/api/v1/admin/tables/roles?limit=5");
    expect(validRes.status).toBe(200);
    const validBody = await validRes.json();
    expect(validBody.success).toBe(true);
    expect(validBody.table).toBe("roles");
    expect(Array.isArray(validBody.rows)).toBe(true);

    const invalidRes = await app.request("/api/v1/admin/tables/sqlite_master");
    expect(invalidRes.status).toBe(400);
  });

  it("should support insert, update, and delete on whitelisted tables", async () => {
    const testRoleId = "role_admin_test_" + Date.now();

    // 1. Insert
    const insertRes = await app.request("/api/v1/admin/tables/roles", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        id: testRoleId,
        name: "TEST_ROLE",
        description: "Test role for admin table manager",
        is_system: 0,
      }),
    });
    expect(insertRes.status).toBe(201);
    const insertBody = await insertRes.json();
    expect(insertBody.success).toBe(true);
    expect(insertBody.row.id).toBe(testRoleId);

    // 2. Update
    const updateRes = await app.request(`/api/v1/admin/tables/roles/${testRoleId}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        description: "Updated description for admin test role",
      }),
    });
    expect(updateRes.status).toBe(200);
    const updateBody = await updateRes.json();
    expect(updateBody.row.description).toBe("Updated description for admin test role");

    // 3. Delete
    const deleteRes = await app.request(`/api/v1/admin/tables/roles/${testRoleId}`, {
      method: "DELETE",
    });
    expect(deleteRes.status).toBe(200);

    // Verify deleted
    const verifyRes = await app.request(`/api/v1/admin/tables/roles/${testRoleId}`, {
      method: "DELETE",
    });
    expect(verifyRes.status).toBe(404);
  });

  it("should re-seed demo data through /api/v1/admin/seed", async () => {
    const seedRes = await app.request("/api/v1/admin/seed", {
      method: "POST",
    });
    expect(seedRes.status).toBe(200);
    const seedBody = await seedRes.json();
    expect(seedBody.success).toBe(true);
  });
});
