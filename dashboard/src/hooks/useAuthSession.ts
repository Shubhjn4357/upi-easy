import { useState, useEffect, useCallback } from 'react';
import {
  getStoredAuth,
  saveAuth,
  clearAuth,
  isTokenExpired,
  getRefreshToken,
  refreshAuthSession,
} from '../lib/auth';
import type { User, Organization, AuthState } from '../types';

export interface UseAuthSessionProps {
  onSessionRestored?: (token: string) => Promise<void> | void;
  onSessionExpired?: () => void;
}

export function useAuthSession({ onSessionRestored, onSessionExpired }: UseAuthSessionProps = {}) {
  const [auth, setAuth] = useState<AuthState>(() => getStoredAuth());
  const token = auth.token;
  const user = auth.user;

  const [isCheckingAuth, setIsCheckingAuth] = useState<boolean>(() => {
    const stored = getStoredAuth();
    const rToken = getRefreshToken();
    return Boolean(rToken && (!stored.token || isTokenExpired(stored.token)));
  });

  // Auto-Authorization & Persistent Session Recovery on mount
  useEffect(() => {
    let isMounted = true;
    const checkAutoAuth = async () => {
      const rToken = getRefreshToken();
      if (rToken && (!token || isTokenExpired(token))) {
        try {
          const freshToken = await refreshAuthSession();
          if (freshToken && isMounted) {
            const updated = getStoredAuth();
            setAuth({ token: freshToken, user: updated.user });
            if (onSessionRestored) {
              await onSessionRestored(freshToken);
            }
          }
        } catch (err) {
          console.error('[AuthSession] Auto session recovery failed:', err);
        } finally {
          if (isMounted) setIsCheckingAuth(false);
        }
      } else {
        if (isMounted) setIsCheckingAuth(false);
      }
    };
    checkAutoAuth();
    return () => {
      isMounted = false;
    };
  }, []);

  const handleLoginSuccess = useCallback(
    async (
      newAccessToken: string,
      newUser: User,
      defaultOrg?: Organization | null,
      refreshToken?: string
    ) => {
      saveAuth(newAccessToken, newUser, refreshToken);
      setAuth({ token: newAccessToken, user: newUser });
      if (onSessionRestored) {
        await onSessionRestored(newAccessToken);
      }
    },
    [onSessionRestored]
  );

  const handleLogout = useCallback(() => {
    clearAuth();
    setAuth({ token: '', user: null });
    onSessionExpired?.();
  }, [onSessionExpired]);

  const handleUnauthorized = useCallback(() => {
    setAuth({ token: '', user: null });
    onSessionExpired?.();
  }, [onSessionExpired]);

  return {
    auth,
    token,
    user,
    isAuthenticated: Boolean(token),
    isCheckingAuth,
    handleLoginSuccess,
    handleLogout,
    handleUnauthorized,
  };
}

export default useAuthSession;
