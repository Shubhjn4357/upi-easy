import { defineConfig } from "drizzle-kit";

export default defineConfig({
  schema: "./dist/db/schema/index.js",
  out: "./drizzle",
  dialect: "sqlite",
  dbCredentials: {
    url: "./upieasy.db",
  },
});
