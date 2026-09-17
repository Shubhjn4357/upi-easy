import { app } from "./app.js";

// Cloudflare Workers fetch handler
export default {
  fetch(request: Request, env: any, ctx: any) {
    return app.fetch(request, env, ctx);
  },
};
