// Authenticated Secure API Client
import { isTokenExpired, clearAuth } from './auth.js';

export function createApiClient(getToken, onUnauthorized, onLatency) {
  return async function apiFetch(endpoint, options = {}) {
    const token = typeof getToken === 'function' ? getToken() : getToken;

    // Check client-side expiration before dispatching
    if (token && isTokenExpired(token)) {
      clearAuth();
      onUnauthorized?.();
      throw new Error("Session expired. Please sign in again.");
    }

    const headers = {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {}),
    };

    const startTime = performance.now();
    try {
      const res = await fetch(`${window.location.origin}${endpoint}`, {
        ...options,
        headers,
      });

      const latency = Math.round(performance.now() - startTime);
      onLatency?.(latency);

      // Automatic 401 Unauthorized handling
      if (res.status === 401) {
        clearAuth();
        onUnauthorized?.();
        throw new Error("Unauthorized session. Please sign in again.");
      }

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.error || data.message || `Request failed with status ${res.status}`);
      }
      return data;
    } catch (err) {
      const latency = Math.round(performance.now() - startTime);
      onLatency?.(latency);
      throw err;
    }
  };
}
