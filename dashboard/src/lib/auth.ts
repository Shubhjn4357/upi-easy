// Security & Authentication Utilities with strict TypeScript types
import type { User, AuthState } from '../types';

export interface JwtPayload {
  sub?: string;
  email?: string;
  role?: string;
  exp?: number;
  iat?: number;
  [key: string]: unknown;
}

export function parseJwtPayload(token: string | null | undefined): JwtPayload | null {
  if (!token || typeof token !== 'string') return null;
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const base64Url = parts[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload);
  } catch {
    return null;
  }
}

const TOKEN_KEY = 'upieasy_token';
const REFRESH_TOKEN_KEY = 'upieasy_refresh_token';
const USER_KEY = 'upieasy_user';
const ACTIVE_ORG_KEY = 'upieasy_active_org_id';

import { ENV } from '../services/env.service';

export function isTokenExpired(token: string | null | undefined): boolean {
  const payload = parseJwtPayload(token);
  if (!payload || !payload.exp) return false;
  // Buffer of 15 seconds to prevent edge-case race conditions
  return Date.now() >= (payload.exp - 15) * 1000;
}

export function getRefreshToken(): string {
  return localStorage.getItem(REFRESH_TOKEN_KEY) || '';
}

export async function refreshAuthSession(): Promise<string | null> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return null;
  try {
    const apiBase = ENV.API_BASE_URL || (typeof window !== 'undefined' ? window.location.origin : '');
    const res = await fetch(`${apiBase}/api/v1/auth/refresh`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) {
      clearAuth();
      return null;
    }
    const data = await res.json();
    if (data.success && data.tokens?.accessToken) {
      saveAuth(data.tokens.accessToken, undefined, data.tokens.refreshToken || refreshToken);
      return data.tokens.accessToken;
    }
  } catch {
    // Network hiccup, keep existing token
  }
  return null;
}

export function getStoredAuth(): AuthState {
  const token = localStorage.getItem(TOKEN_KEY) || '';
  if (!token) return { token: '', user: null };

  const savedUser = localStorage.getItem(USER_KEY);
  let user: User | null = null;
  try {
    user = savedUser ? JSON.parse(savedUser) : null;
  } catch {
    user = null;
  }

  return { token, user };
}

export function saveAuth(token?: string | null, user?: User | null, refreshToken?: string | null): void {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
  if (refreshToken) localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  localStorage.removeItem(ACTIVE_ORG_KEY);
}

export function sanitizeUrl(url?: string): string {
  if (!url) return '#';
  const trimmed = url.trim().toLowerCase();
  if (
    trimmed.startsWith('javascript:') ||
    trimmed.startsWith('data:') ||
    trimmed.startsWith('vbscript:')
  ) {
    return '#';
  }
  return url;
}

export function hasRole(currentRole?: string | null, requiredRole?: string | null): boolean {
  const hierarchy: Record<string, number> = { OWNER: 4, MANAGER: 3, CASHIER: 2, MEMBER: 1 };
  const currentLevel = (currentRole && hierarchy[currentRole.toUpperCase()]) || 0;
  const requiredLevel = (requiredRole && hierarchy[requiredRole.toUpperCase()]) || 0;
  return currentLevel >= requiredLevel;
}
