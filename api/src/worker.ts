import { app } from "./app.js";
import { setD1Database } from "./db/index.js";
import type { AppBindings } from "./types/hono.js";
import type { ExecutionContext } from "@cloudflare/workers-types";

export default {
  async fetch(request: Request, env: AppBindings, ctx: ExecutionContext) {
    const d1 = env?.upi_easy_db || env?.DB;
    if (d1) {
      setD1Database(d1);
    }
    return app.fetch(request, env, ctx);
  },
};
