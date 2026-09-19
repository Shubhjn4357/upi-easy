import { app } from "./app.js";
import { setD1Database } from "./db/index.js";

export default {
  async fetch(request: Request, env: any, ctx: any) {
    const d1 = env?.upi_easy_db || env?.DB;
    if (d1) {
      setD1Database(d1);
    }
    return app.fetch(request, env, ctx);
  },
};
