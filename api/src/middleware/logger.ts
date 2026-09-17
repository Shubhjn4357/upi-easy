import type { MiddlewareHandler } from "hono";
import { generateId } from "../lib/crypto.js";
import { logger } from "../lib/logger.js";
import type { AppEnv } from "../types/hono.js";

export const requestLogger: MiddlewareHandler<AppEnv> = async (c, next) => {
  const requestId = c.req.header("x-request-id") || generateId("req");
  c.set("requestId", requestId);
  c.header("x-request-id", requestId);

  const start = Date.now();
  const { method, url } = c.req;

  await next();

  const durationMs = Date.now() - start;
  const status = c.res.status;

  logger.info(
    {
      requestId,
      method,
      url,
      status,
      durationMs,
    },
    `${method} ${url} -> ${status} (${durationMs}ms)`
  );
};
