// React Hook for Google Identity Services (GSI) Authentication
import { useState, useEffect, useRef, useCallback } from 'react';
import { ENV } from '../services/env.service';
import { AuthService } from '../services/auth.service';
import type { User, Organization, Theme } from '../types';

export interface UseGoogleAuthOptions {
  theme: Theme;
  onSuccess: (token: string, user: User, defaultOrg?: Organization | null, refreshToken?: string) => void;
}

export function useGoogleAuth({ theme, onSuccess }: UseGoogleAuthOptions) {
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const buttonRef = useRef<HTMLDivElement | null>(null);

  const submitIdToken = useCallback(async (idToken: string) => {
    setLoading(true);
    setError(null);
    try {
      const response = await AuthService.authenticateWithGoogleToken(idToken);
      onSuccess(response.tokens.accessToken, response.user, response.defaultOrg, response.tokens.refreshToken);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Google authentication failed';
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [onSuccess]);

  useEffect(() => {
    let attempts = 0;
    const maxAttempts = 50; // Try for up to 5 seconds
    let intervalId: ReturnType<typeof setInterval> | null = null;

    const tryInit = () => {
      attempts++;
      if (typeof window !== 'undefined' && window.google?.accounts?.id) {
        if (intervalId) clearInterval(intervalId);
        try {
          window.google.accounts.id.initialize({
            client_id: ENV.GOOGLE_CLIENT_ID,
            callback: async (response: { credential?: string }) => {
              if (response.credential) {
                await submitIdToken(response.credential);
              }
            },
            auto_select: true,
            cancel_on_tap_outside: false,
          });

          // Trigger One-tap auto sign-in prompt
          window.google.accounts.id.prompt();

          if (buttonRef.current) {
            buttonRef.current.innerHTML = '';
            window.google.accounts.id.renderButton(buttonRef.current, {
              type: 'standard',
              theme: theme === 'dark' ? 'filled_black' : 'outline',
              size: 'large',
              shape: 'pill',
              width: 380,
              text: 'signin_with',
              logo_alignment: 'center',
            });
          }
        } catch (err: unknown) {
          console.error('[GoogleAuth] Failed to initialize GSI:', err);
        }
      } else if (attempts >= maxAttempts) {
        if (intervalId) clearInterval(intervalId);
        console.warn('[GoogleAuth] Google Identity Services script timed out after 5 seconds');
      }
    };

    tryInit();
    if (typeof window !== 'undefined' && !window.google?.accounts?.id) {
      intervalId = setInterval(tryInit, 100);
    }

    return () => {
      if (intervalId) clearInterval(intervalId);
    };
  }, [theme, submitIdToken]);

  interface GooglePromptMomentNotification {
    isNotDisplayed: () => boolean;
    isSkippedMoment: () => boolean;
    isDismissedMoment?: () => boolean;
  }

  const promptOneTap = (onNotDisplayed?: () => void) => {
    if (typeof window !== 'undefined' && window.google?.accounts?.id) {
      window.google.accounts.id.prompt((notification: GooglePromptMomentNotification) => {
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
