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

export type AppEnv = {
  Variables: AppVariables;
};
