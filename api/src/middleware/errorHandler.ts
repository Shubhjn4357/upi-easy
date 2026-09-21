import type { ErrorHandler } from "hono";
import { AppError } from "../lib/errors.js";
import { logger } from "../lib/logger.js";
import { ZodError } from "zod";

export const errorHandler: ErrorHandler = (err, c) => {
  const requestId = c.get("requestId") || "unknown";

  if (err instanceof ZodError) {
    return c.json(
      {
        success: false,
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
        success: false,
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

  const errorMessage = err instanceof Error ? err.message : String(err);
  const errorStack = err instanceof Error ? err.stack : undefined;
  const errorName = err instanceof Error ? err.name : typeof err;

  logger.error(
    {
      errorMessage,
      errorStack,
      errorName,
      requestId,
    },
    `Unhandled server exception: ${errorMessage}`
  );

  return c.json(
    {
      success: false,
      error: {
        code: "INTERNAL_SERVER_ERROR",
        message: "An unexpected error occurred",
        details: errorMessage,
        requestId,
      },
    },
    500
  );
};
