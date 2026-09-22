// HTTP API Service with Strict TypeScript Generics
import { ENV } from './env.service';
import { AuthService } from './auth.service';

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  headers?: Record<string, string>;
}

export class ApiService {
  private static tokenProvider: () => string | null = () => AuthService.getStoredAuth().token;
  private static onUnauthorizedCallback?: () => void;
  private static onLatencyCallback?: (latencyMs: number) => void;

  static initialize(
    tokenProvider: () => string | null,
    onUnauthorized?: () => void,
    onLatency?: (latencyMs: number) => void
  ): void {
    this.tokenProvider = tokenProvider;
    this.onUnauthorizedCallback = onUnauthorized;
    this.onLatencyCallback = onLatency;
  }

  static async request<T = unknown>(endpoint: string, options: RequestOptions = {}): Promise<T> {
    const token = this.tokenProvider();

    // Client-side expiry pre-check
    if (token && AuthService.isTokenExpired(token)) {
      AuthService.clearAuth();
      this.onUnauthorizedCallback?.();
      throw new Error('Session expired. Please sign in again.');
    }

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {}),
    };

    const isJsonBody = options.body !== undefined && typeof options.body === 'object' && !(options.body instanceof FormData);
    const body = isJsonBody ? JSON.stringify(options.body) : (options.body as BodyInit | null | undefined);

    const url = endpoint.startsWith('http') ? endpoint : `${ENV.API_BASE_URL}${endpoint}`;
    const startTime = performance.now();

    try {
      const response = await fetch(url, {
        ...options,
        headers,
        body,
      });

      const latency = Math.round(performance.now() - startTime);
      this.onLatencyCallback?.(latency);

      if (response.status === 401) {
        AuthService.clearAuth();
        this.onUnauthorizedCallback?.();
        throw new Error('Unauthorized session. Please sign in again.');
      }

      const data = await response.json();
      if (!response.ok) {
        throw new Error(data.error || data.message || `Request failed with status ${response.status}`);
      }

      return data as T;
    } catch (error) {
      const latency = Math.round(performance.now() - startTime);
      this.onLatencyCallback?.(latency);
      throw error;
    }
  }

  static get<T = unknown>(endpoint: string, options?: Omit<RequestOptions, 'body'>): Promise<T> {
    return this.request<T>(endpoint, { ...options, method: 'GET' });
  }

  static post<T = unknown>(endpoint: string, body?: unknown, options?: RequestOptions): Promise<T> {
    return this.request<T>(endpoint, { ...options, method: 'POST', body });
  }

  static patch<T = unknown>(endpoint: string, body?: unknown, options?: RequestOptions): Promise<T> {
    return this.request<T>(endpoint, { ...options, method: 'PATCH', body });
  }

  static delete<T = unknown>(endpoint: string, options?: RequestOptions): Promise<T> {
    return this.request<T>(endpoint, { ...options, method: 'DELETE' });
  }
}

export default ApiService;
