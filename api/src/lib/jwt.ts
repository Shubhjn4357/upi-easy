import * as jose from "jose";
import { config } from "../config/index.js";

const secretKey = new TextEncoder().encode(config.JWT_SECRET);

export interface TokenPayload {
  sub: string; // User ID
  mobileNumber: string;
  organizationId?: string;
  role?: string;
  permissions?: string[];
  sessionId: string;
}

export async function createAccessToken(payload: TokenPayload): Promise<string> {
  return await new jose.SignJWT({ ...payload })
    .setProtectedHeader({ alg: "HS256" })
    .setIssuedAt()
    .setExpirationTime(config.JWT_EXPIRY)
    .sign(secretKey);
}

export async function createRefreshToken(userId: string, sessionId: string): Promise<string> {
  return await new jose.SignJWT({ sub: userId, sessionId, type: "refresh" })
    .setProtectedHeader({ alg: "HS256" })
    .setIssuedAt()
    .setExpirationTime(config.REFRESH_TOKEN_EXPIRY)
    .sign(secretKey);
}

export async function verifyToken<T = TokenPayload>(token: string): Promise<T> {
  const { payload } = await jose.jwtVerify(token, secretKey);
  return payload as unknown as T;
}
