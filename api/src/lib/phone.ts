import { AppError } from "./errors.js";

/**
 * Normalizes an Indian mobile number into canonical E.164 format (+91XXXXXXXXXX).
 * Handles inputs with '+91', '91', leading '0', spaces, dashes, parentheses.
 * Validates that the 10-digit number starts with 6, 7, 8, or 9.
 */
export function normalizeIndianMobileNumber(mobile: string): string {
  if (!mobile || typeof mobile !== "string") {
    throw new AppError("Mobile number is required", 400, "INVALID_MOBILE_NUMBER");
  }

  // Strip all non-digit characters
  let cleaned = mobile.replace(/\D/g, "");

  // If starts with 91 and has 12 digits, strip country code
  if (cleaned.length === 12 && cleaned.startsWith("91")) {
    cleaned = cleaned.substring(2);
  } else if (cleaned.length === 11 && cleaned.startsWith("0")) {
    // If starts with 0 and has 11 digits, strip leading 0
    cleaned = cleaned.substring(1);
  }

  // Must now be exactly 10 digits and start with 6, 7, 8, or 9
  if (cleaned.length !== 10 || !/^[6-9]\d{9}$/.test(cleaned)) {
    throw new AppError(
      "Invalid Indian mobile number. Must be a 10-digit number starting with 6, 7, 8, or 9.",
      400,
      "INVALID_MOBILE_NUMBER"
    );
  }

  return `+91${cleaned}`;
}

/**
 * Returns the raw 10-digit representation (without +91).
 */
export function get10DigitMobile(mobile: string): string {
  const normalized = normalizeIndianMobileNumber(mobile);
  return normalized.substring(3);
}
