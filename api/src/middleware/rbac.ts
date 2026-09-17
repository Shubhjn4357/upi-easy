import type { MiddlewareHandler } from "hono";
import { ForbiddenError } from "../lib/errors.js";
import type { AppEnv } from "../types/hono.js";

export function requirePermission(permission: string): MiddlewareHandler<AppEnv> {
  return async (c, next) => {
    const role = c.get("role");
    const permissions: string[] = c.get("permissions") || [];

    // Owner role has implicit full permissions
    if (role === "OWNER") {
      return await next();
    }

    if (!permissions.includes(permission)) {
      throw new ForbiddenError(`Missing required permission: ${permission}`);
    }

    await next();
  };
}
