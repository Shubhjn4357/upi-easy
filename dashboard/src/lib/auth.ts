// Security & Authentication Utilities with strict TypeScript types
import type { User, AuthState, UserRole } from '../types';

export interface JwtPayload {
  sub?: string;
  email?: string;
  role?: string;
  exp?: number;
  iat?: number;
  [key: string]: any;
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

export function isTokenExpired(token: string | null | undefined): boolean {
  const payload = parseJwtPayload(token);
  if (!payload || !payload.exp) return false;
  // Buffer of 10 seconds to prevent edge-case race conditions
  return Date.now() >= (payload.exp - 10) * 1000;
}

export function getStoredAuth(): AuthState {
  const token = localStorage.getItem('upieasy_token') || '';
  if (!token) return { token: '', user: null };

  if (isTokenExpired(token)) {
    clearAuth();
    return { token: '', user: null };
  }

  const savedUser = localStorage.getItem('upieasy_user');
  let user: User | null = null;
  try {
    user = savedUser ? JSON.parse(savedUser) : null;
  } catch {
    user = null;
  }

  return { token, user };
}

export function saveAuth(token?: string | null, user?: User | null): void {
  if (token) localStorage.setItem('upieasy_token', token);
  if (user) localStorage.setItem('upieasy_user', JSON.stringify(user));
}

export function clearAuth(): void {
  localStorage.removeItem('upieasy_token');
  localStorage.removeItem('upieasy_user');
  localStorage.removeItem('upieasy_active_org_id');
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
