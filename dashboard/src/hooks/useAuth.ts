// React Hook for User Authentication State
import { useState, useCallback } from 'react';
import { AuthService } from '../services/auth.service';
import type { User, AuthState } from '../types';

export function useAuth() {
  const [auth, setAuth] = useState<AuthState>(() => AuthService.getStoredAuth());

  const login = useCallback((token: string, user: User) => {
    AuthService.saveAuth(token, user);
    setAuth({ token, user });
  }, []);

  const logout = useCallback(() => {
    AuthService.clearAuth();
    setAuth({ token: '', user: null });
  }, []);

  return {
    auth,
    token: auth.token,
    user: auth.user,
    isAuthenticated: Boolean(auth.token),
    login,
    logout,
  };
}

export default useAuth;
