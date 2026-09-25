import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from '../lib/router';
import { Button } from '../components/ui/button';
import { Card, CardHeader, CardTitle, CardDescription } from '../components/ui/card';
import { Badge } from '../components/ui/badge';
import { Alert, AlertDescription } from '../components/ui/components';
import { IconGoogle, IconUsers, IconShield, IconCheck, IconSun, IconMoon } from '../components/ui/icons';
import { useGoogleAuth } from '../hooks/useGoogleAuth';
import type { User, Organization, Theme } from '../types';

export interface InviteAcceptPageProps {
  token?: string | null;
  currentUser?: User | null;
  apiFetch: <T = unknown>(endpoint: string, options?: RequestInit) => Promise<T>;
  showToast: (message: string, type?: 'success' | 'error' | 'info') => void;
  onLoginSuccess: (token: string, user: User, defaultOrg?: Organization | null, refreshToken?: string) => Promise<void>;
  onInviteAccepted: (org: Organization) => Promise<void>;
  theme: Theme;
  onToggleTheme: () => void;
}

interface VerifiedInvite {
  id: string;
  token?: string;
  organizationId: string;
  organizationName: string;
  role: string;
  status: string;
  invitedEmail: string;
  invitedName?: string;
  expiresAt: string;
}

export function InviteAcceptPage({
  token: authToken,
  currentUser,
  apiFetch,
  showToast,
  onLoginSuccess,
  onInviteAccepted,
  theme,
  onToggleTheme,
}: InviteAcceptPageProps) {
  const { token: routeToken } = useParams<{ token?: string }>();
  const navigate = useNavigate();

  // Fallback token extraction from window location if routeToken is empty
  const inviteToken = routeToken || (() => {
    const match = window.location.hash.match(/invite\/([^/?#]+)/) || window.location.pathname.match(/invite\/([^/?#]+)/);
    return match ? match[1] : '';
  })();

  const [loading, setLoading] = useState<boolean>(Boolean(inviteToken));
  const [verifyingError, setVerifyingError] = useState<string | null>(
    inviteToken ? null : 'No invitation token provided in URL.'
  );
  const [invite, setInvite] = useState<VerifiedInvite | null>(null);
  const [isAccepting, setIsAccepting] = useState<boolean>(false);

  // Google Auth hook for unauthenticated sign-in
  const {
    loading: googleLoading,
    error: googleError,
    buttonRef,
    promptOneTap,
  } = useGoogleAuth({
    theme,
    onSuccess: async (newAccessToken, newUser, defaultOrg, refreshToken) => {
      await onLoginSuccess(newAccessToken, newUser, defaultOrg, refreshToken);
      showToast(`Welcome ${newUser.fullName || newUser.name || ''}! Joined successfully.`);
      navigate('/overview');
    },
  });

  // Verify invitation on mount
  useEffect(() => {
    if (!inviteToken) return;
    let isCancelled = false;

    (async () => {
      try {
        const res = await apiFetch<{
          success: boolean;
          isValid: boolean;
          invite: VerifiedInvite;
          message?: string;
        }>(`/api/v1/invitations/verify/${encodeURIComponent(inviteToken)}`);

        if (isCancelled) return;
        if (res.success && res.invite) {
          if (!res.isValid) {
            setVerifyingError('This invitation has expired or has already been used.');
          } else {
            setInvite(res.invite);
          }
        } else {
          setVerifyingError(res.message || 'Invalid or unknown invitation.');
        }
      } catch (err: unknown) {
        if (isCancelled) return;
        const msg = err instanceof Error ? err.message : 'Failed to verify invitation link.';
        setVerifyingError(msg);
      } finally {
        if (!isCancelled) {
          setLoading(false);
        }
      }
    })();

    return () => {
      isCancelled = true;
    };
  }, [inviteToken, apiFetch]);

  // Handle accepting when authenticated
  const handleAcceptInvite = async () => {
    if (!inviteToken) return;
    setIsAccepting(true);
    try {
      const res = await apiFetch<{
        success: boolean;
        organization: Organization;
        message?: string;
      }>(`/api/v1/invitations/${encodeURIComponent(inviteToken)}/accept`, {
        method: 'POST',
      });

      if (res.success && res.organization) {
        showToast(`Joined ${res.organization.name || 'Organization'}!`);
        await onInviteAccepted(res.organization);
        navigate('/overview');
      } else {
        showToast(res.message || 'Failed to accept invitation', 'error');
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error accepting invitation';
      showToast(msg, 'error');
    } finally {
      setIsAccepting(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 relative bg-background transition-colors">
      {/* Top right theme toggle */}
      <div className="absolute top-4 right-4">
        <Button
          variant="outline"
          size="sm"
          onClick={onToggleTheme}
          className="rounded-xl gap-1.5 text-xs">
          {theme === 'dark' ? (
            <IconSun className="w-3.5 h-3.5 text-amber-400" />
          ) : (
            <IconMoon className="w-3.5 h-3.5 text-indigo-500" />
          )}
        </Button>
      </div>

      <Card className="w-full max-w-md rounded-3xl p-6 sm:p-8 shadow-2xl relative overflow-hidden border border-border">
        {/* Loading State */}
        {loading && (
          <div className="py-12 flex flex-col items-center justify-center text-center space-y-4">
            <div className="w-12 h-12 rounded-full border-4 border-brand-500 border-t-transparent animate-spin" />
            <div className="space-y-1">
              <h3 className="text-base font-bold text-foreground">Verifying Invitation</h3>
              <p className="text-xs text-muted-foreground">Checking team invitation details...</p>
            </div>
          </div>
        )}

        {/* Error / Expired State */}
        {!loading && verifyingError && (
          <div className="py-6 text-center space-y-5">
            <div className="w-14 h-14 rounded-2xl bg-destructive/10 text-destructive flex items-center justify-center mx-auto">
              <IconShield className="w-7 h-7" />
            </div>
            <div className="space-y-2">
              <CardTitle className="text-lg font-extrabold text-foreground">
                Invitation Unavailable
              </CardTitle>
              <CardDescription className="text-xs text-muted-foreground max-w-xs mx-auto">
                {verifyingError}
              </CardDescription>
            </div>

            <Button
              variant="brand"
              onClick={() => navigate('/overview')}
              className="w-full rounded-xl py-3 text-xs font-semibold">
              Go to Dashboard
            </Button>
          </div>
        )}

        {/* Valid Invitation Details */}
        {!loading && !verifyingError && invite && (
          <div className="space-y-6">
            {/* Header */}
            <CardHeader className="text-center p-0 space-y-3">
              <div className="w-14 h-14 rounded-2xl bg-gradient-to-tr from-brand-600 to-indigo-500 flex items-center justify-center mx-auto shadow-lg shadow-brand-500/25">
                <IconUsers className="w-7 h-7 text-white" />
              </div>
              <div>
                <span className="text-[11px] font-semibold text-brand-600 dark:text-cyan-400 uppercase tracking-wider">
                  Team Invitation
                </span>
                <CardTitle className="text-xl font-extrabold tracking-tight mt-0.5 text-foreground">
                  {invite.organizationName}
                </CardTitle>
                <CardDescription className="text-xs text-muted-foreground mt-1">
                  You have been invited to join as
                </CardDescription>
              </div>
              <div className="flex justify-center">
                <Badge variant="secondary" className="px-3 py-1 text-xs font-bold uppercase tracking-wider">
                  {invite.role}
                </Badge>
              </div>
            </CardHeader>

            {/* Invite Details Box */}
            <div className="p-3.5 rounded-2xl bg-muted/40 border border-border/80 space-y-2 text-xs">
              <div className="flex items-center justify-between text-muted-foreground">
                <span>Invited Email:</span>
                <span className="font-semibold text-foreground font-mono">{invite.invitedEmail}</span>
              </div>
              {invite.invitedName && (
                <div className="flex items-center justify-between text-muted-foreground">
                  <span>Name:</span>
                  <span className="font-semibold text-foreground">{invite.invitedName}</span>
                </div>
              )}
              <div className="flex items-center justify-between text-muted-foreground">
                <span>Link Expiry:</span>
                <span className="text-[11px]">{new Date(invite.expiresAt).toLocaleDateString()}</span>
              </div>
            </div>

            {googleError && (
              <Alert variant="destructive">
                <AlertDescription>{googleError}</AlertDescription>
              </Alert>
            )}

            {/* Action State: If already signed in */}
            {authToken ? (
              <div className="space-y-3 pt-2">
                <div className="text-xs text-center text-muted-foreground">
                  Signed in as <span className="font-semibold text-foreground">{currentUser?.email}</span>
                </div>

                <Button
                  variant="brand"
                  disabled={isAccepting}
                  onClick={handleAcceptInvite}
                  className="w-full py-5 rounded-2xl font-bold text-sm shadow-md gap-2">
                  {isAccepting ? (
                    <>
                      <div className="w-4 h-4 rounded-full border-2 border-white border-t-transparent animate-spin" />
                      <span>Accepting Invitation...</span>
                    </>
                  ) : (
                    <>
                      <IconCheck className="w-4 h-4" />
                      <span>Accept & Join {invite.organizationName}</span>
                    </>
                  )}
                </Button>
              </div>
            ) : (
              /* Action State: If not signed in yet */
              <div className="space-y-3 pt-2">
                <p className="text-xs text-center text-muted-foreground">
                  Sign in with your Google account to automatically join this store and bypass setup.
                </p>

                {/* Google Sign-In Button */}
                <div className="relative w-full overflow-hidden rounded-2xl group">
                  <Button
                    type="button"
                    variant="outline"
                    disabled={googleLoading}
                    onClick={() => promptOneTap()}
                    className="w-full flex items-center justify-center gap-3 py-5 rounded-2xl border-border bg-card hover:bg-accent/40 font-semibold text-xs sm:text-sm shadow-sm transition-all duration-200 group-hover:border-brand-500/50">
                    <IconGoogle className="w-5 h-5 flex-shrink-0" />
                    <span>{googleLoading ? 'Connecting to Google...' : 'Sign in with Google to Accept'}</span>
                  </Button>

                  {/* Transparent Google GSI overlay */}
                  <div
                    ref={buttonRef}
                    title="Sign in with Google"
                    className="absolute inset-0 opacity-[0.0001] cursor-pointer overflow-hidden z-10 flex items-center justify-center [&>div]:!w-full [&>div]:!h-full [&_iframe]:!w-full [&_iframe]:!h-full [&_iframe]:!cursor-pointer"
                  />
                </div>
              </div>
            )}
          </div>
        )}
      </Card>
    </div>
  );
}

export default InviteAcceptPage;
