import { Hono } from "hono";
import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq, desc } from "drizzle-orm";
import { requireAuth } from "../../middleware/auth.js";
import { requireTenant } from "../../middleware/tenant.js";
import { requirePermission } from "../../middleware/rbac.js";
import type { AppEnv } from "../../types/hono.js";

export const auditRouter = new Hono<AppEnv>();

auditRouter.use("*", requireAuth);

auditRouter.get("/:orgId/audit", requireTenant, requirePermission("organization.manage"), async (c) => {
  const orgId = c.get("organizationId");
  const limit = Math.min(Number(c.req.query("limit")) || 50, 100);

  const logs = db
    .select({
      id: schema.auditLogs.id,
      action: schema.auditLogs.action,
      resourceType: schema.auditLogs.resourceType,
      resourceId: schema.auditLogs.resourceId,
      metadata: schema.auditLogs.metadataJson,
      ipAddress: schema.auditLogs.ipAddress,
      actorMobile: schema.users.mobileNumber,
      createdAt: schema.auditLogs.createdAt,
    })
    .from(schema.auditLogs)
    .leftJoin(schema.users, eq(schema.auditLogs.actorId, schema.users.id))
    .where(eq(schema.auditLogs.organizationId, orgId))
    .orderBy(desc(schema.auditLogs.createdAt))
    .limit(limit)
    .all();

  return c.json({
    success: true,
    auditLogs: logs.map((l: any) => ({
      ...l,
      metadata: l.metadata ? JSON.parse(l.metadata) : null,
    })),
  });
});
