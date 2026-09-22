// React Hook for Google Identity Services (GSI) Authentication
import { useState, useEffect, useRef } from 'react';
import { ENV } from '../services/env.service';
import { AuthService } from '../services/auth.service';
import type { User, Theme } from '../types';

export interface UseGoogleAuthOptions {
  theme: Theme;
  onSuccess: (token: string, user: User) => void;
}

export function useGoogleAuth({ theme, onSuccess }: UseGoogleAuthOptions) {
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const buttonRef = useRef<HTMLDivElement | null>(null);

  const submitIdToken = async (idToken: string) => {
    setLoading(true);
    setError(null);
    try {
      const response = await AuthService.authenticateWithGoogleToken(idToken);
      onSuccess(response.tokens.accessToken, response.user);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Google authentication failed';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const initGsi = () => {
      if (typeof window !== 'undefined' && window.google?.accounts?.id) {
        window.google.accounts.id.initialize({
          client_id: ENV.GOOGLE_CLIENT_ID,
          callback: async (response: { credential?: string }) => {
            if (response.credential) {
              await submitIdToken(response.credential);
            }
          },
          auto_select: false,
          cancel_on_tap_outside: true,
        });

        if (buttonRef.current) {
          buttonRef.current.innerHTML = '';
          window.google.accounts.id.renderButton(buttonRef.current, {
            type: 'standard',
            theme: theme === 'dark' ? 'filled_black' : 'outline',
            size: 'large',
            shape: 'pill',
            width: 320,
            text: 'signin_with',
            logo_alignment: 'center',
          });
        }
      }
    };

    const timer = setTimeout(initGsi, 200);
    return () => clearTimeout(timer);
  }, [theme]);

  const promptOneTap = (onNotDisplayed?: () => void) => {
    if (typeof window !== 'undefined' && window.google?.accounts?.id) {
      window.google.accounts.id.prompt((notification: any) => {
        if (notification.isNotDisplayed() || notification.isSkippedMoment()) {
          onNotDisplayed?.();
        }
      });
    } else {
      onNotDisplayed?.();
    }
  };

  return {
    loading,
    error,
    buttonRef,
    submitIdToken,
    promptOneTap,
  };
}

export default useGoogleAuth;
