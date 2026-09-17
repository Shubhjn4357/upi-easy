import type { ErrorHandler } from "hono";
import { AppError } from "../lib/errors.js";
import { logger } from "../lib/logger.js";
import { ZodError } from "zod";

export const errorHandler: ErrorHandler = (err, c) => {
  const requestId = c.get("requestId") || "unknown";

  if (err instanceof ZodError) {
    return c.json(
      {
        error: {
          code: "VALIDATION_ERROR",
          message: "Input validation failed",
          details: err.flatten().fieldErrors,
          requestId,
        },
      },
      400
    );
  }

  if (err instanceof AppError) {
    return c.json(
      {
        error: {
          code: err.code,
          message: err.message,
          details: err.details,
          requestId,
        },
      },
      err.statusCode as any
    );
  }

  logger.error({ err, requestId }, "Unhandled server exception");

  return c.json(
    {
      error: {
        code: "INTERNAL_SERVER_ERROR",
        message: "An unexpected error occurred",
        requestId,
      },
    },
    500
  );
};
