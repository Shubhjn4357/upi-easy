import type { D1Database } from "@cloudflare/workers-types";

export interface AppVariables {
  userId: string;
  sessionId: string;
  mobileNumber: string;
  organizationId: string;
  memberId: string;
  role: string;
  permissions: string[];
  requestId: string;
}

export interface AppBindings {
  upi_easy_db?: D1Database;
  DB?: D1Database;
  GOOGLE_WEB_CLIENT_ID?: string;
  API_BASE_URL?: string;
  JWT_SECRET?: string;
  WEBHOOK_SECRET?: string;
}

export type AppEnv = {
  Bindings: AppBindings;
  Variables: AppVariables;
};
