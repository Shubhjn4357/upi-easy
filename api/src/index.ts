import { serve } from "@hono/node-server";
import { app } from "./app.js";
import { config } from "./config/index.js";
import { logger } from "./lib/logger.js";
import { initDatabase } from "./db/index.js";

initDatabase();

const server = serve(
  {
    fetch: app.fetch,
    port: config.PORT,
  },
  (info) => {
    logger.info(`UPI-Easy Backend listening on http://localhost:${info.port}`);
  }
);

const gracefulShutdown = () => {
  logger.info("Received termination signal. Shutting down gracefully...");
  server.close(() => {
    logger.info("HTTP server closed.");
    process.exit(0);
  });
};

process.on("SIGINT", gracefulShutdown);
process.on("SIGTERM", gracefulShutdown);
