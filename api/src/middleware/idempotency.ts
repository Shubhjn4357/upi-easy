import type { MiddlewareHandler } from "hono";
import { db } from "../db/index.js";
import * as schema from "../db/schema/index.js";
import { eq } from "drizzle-orm";
import { hashString, generateId } from "../lib/crypto.js";
import type { AppEnv } from "../types/hono.js";

export const handleIdempotency: MiddlewareHandler<AppEnv> = async (c, next) => {
  const idempotencyKey = c.req.header("idempotency-key");
  if (!idempotencyKey) {
    return await next();
  }

  const orgId = c.req.param("orgId") || c.get("organizationId");
  const operation = `${c.req.method} ${c.req.path}`;

  // Check if this key was already processed
  const existing = db
    .select()
    .from(schema.idempotencyKeys)
    .where(eq(schema.idempotencyKeys.idempotencyKey, idempotencyKey))
    .get();

  if (existing) {
    c.header("x-idempotent-replayed", "true");
    return c.newResponse(existing.responseBody, {
      status: existing.responseStatus as any,
      headers: {
        "content-type": "application/json",
        "x-idempotent-replayed": "true",
      },
    });
  }

  // Clone request body for hash without consuming downstream body stream
  let rawBody = "";
  try {
    rawBody = await c.req.raw.clone().text();
  } catch {
    rawBody = "";
  }
  const requestHash = hashString(rawBody);

  await next();

  // Save the response if it was successful (status < 400)
  const responseStatus = c.res.status;
  if (responseStatus < 400) {
    try {
      const clonedRes = c.res.clone();
      const responseBody = await clonedRes.text();

      db.insert(schema.idempotencyKeys)
        .values({
          id: generateId("idem"),
          idempotencyKey,
          organizationId: orgId ?? null,
          operation,
          requestHash,
          responseStatus,
          responseBody,
          createdAt: new Date(),
        })
        .run();
    } catch {
      // Non-blocking on persistence failure
    }
  }
};
