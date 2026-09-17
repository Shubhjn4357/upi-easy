import type { MiddlewareHandler } from "hono";
import { RateLimitError } from "../lib/errors.js";

interface RateLimitStore {
  count: number;
  resetTime: number;
}

const memoryStore = new Map<string, RateLimitStore>();

export function rateLimit(options: {
  max: number;
  windowMs: number;
  keyGenerator?: (c: any) => string;
}): MiddlewareHandler {
  return async (c, next) => {
    const key = options.keyGenerator
      ? options.keyGenerator(c)
      : c.req.header("cf-connecting-ip") || c.req.header("x-forwarded-for") || "global";

    const now = Date.now();
    const entry = memoryStore.get(key);

    if (!entry || now > entry.resetTime) {
      memoryStore.set(key, { count: 1, resetTime: now + options.windowMs });
      return await next();
    }

    if (entry.count >= options.max) {
      const retryAfterSec = Math.ceil((entry.resetTime - now) / 1000);
      c.header("retry-after", retryAfterSec.toString());
      throw new RateLimitError(`Rate limit exceeded. Try again in ${retryAfterSec} seconds.`);
    }

    entry.count += 1;
    await next();
  };
}
