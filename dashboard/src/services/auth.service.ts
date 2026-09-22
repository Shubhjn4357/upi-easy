// Authentication & Session Service (Strictly Typed)
import { ENV } from './env.service';
import type { User, AuthState, AuthResponse } from '../types';

const TOKEN_KEY = 'upieasy_token';
const USER_KEY = 'upieasy_user';
const ACTIVE_ORG_KEY = 'upieasy_active_org_id';

export interface JwtPayload {
  sub?: string;
  email?: string;
  role?: string;
  exp?: number;
  iat?: number;
  [key: string]: unknown;
}

export class AuthService {
  static parseJwtPayload(token: string | null | undefined): JwtPayload | null {
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
      return JSON.parse(jsonPayload) as JwtPayload;
    } catch {
      return null;
    }
  }

  static isTokenExpired(token: string | null | undefined): boolean {
    const payload = this.parseJwtPayload(token);
    if (!payload || !payload.exp) return false;
    // Buffer of 10 seconds to prevent race conditions
    return Date.now() >= (payload.exp - 10) * 1000;
  }

  static getStoredAuth(): AuthState {
    const token = localStorage.getItem(TOKEN_KEY) || '';
    if (!token) return { token: '', user: null };

    if (this.isTokenExpired(token)) {
      this.clearAuth();
      return { token: '', user: null };
    }

    const savedUser = localStorage.getItem(USER_KEY);
    let user: User | null = null;
    try {
      user = savedUser ? (JSON.parse(savedUser) as User) : null;
    } catch {
      user = null;
    }

    return { token, user };
  }

  static saveAuth(token?: string | null, user?: User | null): void {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
  }

  static clearAuth(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(ACTIVE_ORG_KEY);
  }

  static async authenticateWithGoogleToken(idToken: string): Promise<AuthResponse> {
    const url = `${ENV.API_BASE_URL}/api/v1/auth/google`;
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
      body: JSON.stringify({
        idToken,
        deviceId: ENV.DEVICE_ID,
      }),
    });

    const contentType = response.headers.get('content-type') || '';
    if (!contentType.includes('application/json')) {
      const text = await response.text();
      console.error(`[AuthService] Expected JSON but received HTML/Text from ${url} (Status ${response.status}):`, text.substring(0, 300));
      throw new Error(`API returned unexpected response (status ${response.status}). Expected JSON.`);
    }

    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.error?.message || data.error || data.message || `Google authentication failed (${response.status})`);
    }

    return data as AuthResponse;
  }
}

export default AuthService;
