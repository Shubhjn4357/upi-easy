import crypto from "crypto";

export function generateId(prefix = "id"): string {
  const random = crypto.randomBytes(12).toString("hex");
  return `${prefix}_${random}`;
}

export function generateOtp(): string {
  // Cryptographically secure 6-digit OTP
  return crypto.randomInt(100000, 999999).toString();
}

export function hashString(content: string): string {
  return crypto.createHash("sha256").update(content).digest("hex");
}

export function signHmac(secret: string, payload: string): string {
  return crypto.createHmac("sha256", secret).update(payload).digest("hex");
}

export function verifyHmac(secret: string, payload: string, signature: string): boolean {
  try {
    const expected = signHmac(secret, payload);
    return crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(signature));
  } catch {
    return false;
  }
}
