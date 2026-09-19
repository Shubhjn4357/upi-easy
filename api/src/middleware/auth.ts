import type { MiddlewareHandler } from "hono";
import { verifyToken, type TokenPayload } from "../lib/jwt.js";
import { UnauthorizedError } from "../lib/errors.js";
import { db } from "../db/index.js";
import * as schema from "../db/schema/index.js";
import { eq, and } from "drizzle-orm";
import type { AppEnv } from "../types/hono.js";

export const requireAuth: MiddlewareHandler<AppEnv> = async (c, next) => {
  const authHeader = c.req.header("authorization");
  if (!authHeader || !authHeader.startsWith("Bearer ")) {
    throw new UnauthorizedError("Missing or invalid Authorization header");
  }

  const token = authHeader.substring(7).trim();
  let payload: TokenPayload;

  try {
    payload = await verifyToken<TokenPayload>(token);
  } catch (e) {
    throw new UnauthorizedError("Invalid or expired access token");
  }

  // Verify session in database
  const session = await db
    .select()
    .from(schema.sessions)
    .where(
      and(
        eq(schema.sessions.id, payload.sessionId),
        eq(schema.sessions.isRevoked, false)
      )
    )
    .get();

  if (!session) {
    throw new UnauthorizedError("Session has been revoked or expired");
  }

  c.set("userId", payload.sub);
  c.set("mobileNumber", payload.mobileNumber);
  c.set("sessionId", payload.sessionId);

  await next();
};
