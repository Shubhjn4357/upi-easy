// Authenticated Secure API Client with strict TypeScript types
import { isTokenExpired, clearAuth } from './auth';
import { ENV } from '../services/env.service';

export type TokenProvider = string | null | (() => string | null);
export type OrgIdProvider = string | null | (() => string | null);

export interface ApiFetchOptions extends RequestInit {
  headers?: Record<string, string>;
}

export type ApiClient = <T = any>(endpoint: string, options?: ApiFetchOptions) => Promise<T>;

export function createApiClient(
  getToken: TokenProvider,
  onUnauthorized?: () => void,
  onLatency?: (latencyMs: number) => void,
  getOrgId?: OrgIdProvider
): ApiClient {
  return async function apiFetch<T = any>(endpoint: string, options: ApiFetchOptions = {}): Promise<T> {
    const token = typeof getToken === 'function' ? getToken() : getToken;
    const orgId = typeof getOrgId === 'function' ? getOrgId() : getOrgId;

    // Check client-side expiration before dispatching
    if (token && isTokenExpired(token)) {
      clearAuth();
      onUnauthorized?.();
      throw new Error('Session expired. Please sign in again.');
    }

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(orgId ? { 'X-Organization-Id': orgId } : {}),
      ...(options.headers || {}),
    };

    const baseUrl = ENV.API_BASE_URL || (typeof window !== 'undefined' ? window.location.origin : '');
    const url = endpoint.startsWith('http') ? endpoint : `${baseUrl}${endpoint}`;

    const startTime = performance.now();
    try {
      const res = await fetch(url, {
        ...options,
        headers,
      });

      const latency = Math.round(performance.now() - startTime);
      onLatency?.(latency);

      // Automatic 401 Unauthorized handling
      if (res.status === 401) {
        clearAuth();
        onUnauthorized?.();
        throw new Error('Unauthorized session. Please sign in again.');
      }

      const contentType = res.headers.get('content-type') || '';
      if (!contentType.includes('application/json')) {
        const text = await res.text();
        throw new Error(`Server returned unexpected format (${res.status}): ${text.substring(0, 100)}`);
      }

      const data = await res.json();
      if (!res.ok) {
        const msg = data.error?.message || data.error || data.message || `Request failed with status ${res.status}`;
        throw new Error(msg);
      }
      return data as T;
    } catch (err) {
      const latency = Math.round(performance.now() - startTime);
      onLatency?.(latency);
      throw err;
    }
  };
}
