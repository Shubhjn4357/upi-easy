import pino from "pino";
import { config } from "../config/index.js";

export const logger = pino({
  level: config.NODE_ENV === "test" ? "silent" : "info",
  redact: {
    paths: [
      "password",
      "pin",
      "upiPin",
      "otp",
      "authorization",
      "accessToken",
      "refreshToken",
      "secret",
      "apiKey",
      "token",
      "headers.authorization",
    ],
    censor: "[REDACTED]",
  },
  transport:
    config.NODE_ENV === "development"
      ? {
          target: "pino-pretty",
          options: {
            colorize: true,
            ignore: "pid,hostname",
          },
        }
      : undefined,
});
