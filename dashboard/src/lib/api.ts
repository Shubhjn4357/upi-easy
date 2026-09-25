import { isTokenExpired, clearAuth, refreshAuthSession } from './auth';
import { ENV } from '../services/env.service';

export type TokenProvider = string | null | (() => string | null);
export type OrgIdProvider = string | null | (() => string | null);

export interface ApiFetchOptions extends RequestInit {
  headers?: Record<string, string>;
}

export type ApiClient = <T = unknown>(endpoint: string, options?: ApiFetchOptions) => Promise<T>;

export function createApiClient(
  getToken: TokenProvider,
  onUnauthorized?: () => void,
  onLatency?: (latencyMs: number) => void,
  getOrgId?: OrgIdProvider
): ApiClient {
  return async function apiFetch<T = unknown>(endpoint: string, options: ApiFetchOptions = {}): Promise<T> {
    let token = typeof getToken === 'function' ? getToken() : getToken;
    const orgId = typeof getOrgId === 'function' ? getOrgId() : getOrgId;

    // Check client-side expiration before dispatching - attempt silent refresh
    if (token && isTokenExpired(token)) {
      const freshToken = await refreshAuthSession();
      if (freshToken) {
        token = freshToken;
      } else {
        clearAuth();
        onUnauthorized?.();
        throw new Error('Session expired. Please sign in again.');
      }
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
      let res = await fetch(url, {
        ...options,
        headers,
      });

      const latency = Math.round(performance.now() - startTime);
      onLatency?.(latency);

      // Automatic 401 Unauthorized handling with silent refresh attempt
      if (res.status === 401) {
        const freshToken = await refreshAuthSession();
        if (freshToken) {
          // Retry the request with the refreshed token
          headers.Authorization = `Bearer ${freshToken}`;
          res = await fetch(url, {
            ...options,
            headers,
          });
        }
        
        if (res.status === 401) {
          clearAuth();
          onUnauthorized?.();
          throw new Error('Unauthorized session. Please sign in again.');
        }
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
