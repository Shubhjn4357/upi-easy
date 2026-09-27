import { app } from "./app.js";
import { setD1Database } from "./db/index.js";
import type { AppBindings } from "./types/hono.js";
import type { ExecutionContext } from "@cloudflare/workers-types";

export default {
  async fetch(request: Request, env: AppBindings, ctx: ExecutionContext) {
    try {
      const d1 = env?.upi_easy_db || env?.DB;
      if (d1) {
        setD1Database(d1);
      }
      return await app.fetch(request, env, ctx);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      const stack = err instanceof Error ? err.stack : undefined;
      return new Response(
        JSON.stringify({
          success: false,
          error: {
            code: "WORKER_EXCEPTION",
            message: msg || "Internal server error in worker",
            details: stack,
          },
        }),
        {
          status: 500,
          headers: { "Content-Type": "application/json" },
        }
      );
    }
  },
};
