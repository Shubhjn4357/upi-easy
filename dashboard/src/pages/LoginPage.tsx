import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Alert, AlertDescription } from '@/components/ui/components';
import { IconLock, IconMoon, IconSun, IconGoogle } from '@/components/ui/icons';
import { useGoogleAuth } from '@/hooks/useGoogleAuth';
import type { LoginPageProps } from '@/types';

// Authentication Page: Official Google Sign-In using services & hooks architecture
export function LoginPage({ onLoginSuccess, theme, onToggleTheme, pingMs: _pingMs }: LoginPageProps) {
  const [showManualToken, setShowManualToken] = useState<boolean>(false);
  const [manualTokenInput, setManualTokenInput] = useState<string>('');

  const { loading, error, buttonRef, submitIdToken, promptOneTap } = useGoogleAuth({
    theme,
    onSuccess: onLoginSuccess,
  });

  const handleManualSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!manualTokenInput.trim()) return;
    await submitIdToken(manualTokenInput.trim());
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
            <>
              <IconSun className="w-3.5 h-3.5 text-amber-400" />
            </>
          ) : (
            <>
              <IconMoon className="w-3.5 h-3.5 text-indigo-500" />
            </>
          )}
        </Button>
      </div>

      <Card className="w-full max-w-sm rounded-3xl p-6 sm:p-8 shadow-2xl relative overflow-hidden">
        {/* Brand Header */}
        <CardHeader className="text-center p-0 pb-6">
          <div className="w-14 h-14 rounded-2xl bg-gradient-to-tr from-brand-600 to-indigo-500 flex items-center justify-center mx-auto shadow-md shadow-brand-500/25 mb-4">
            <span className="text-xl font-black text-white tracking-tight">UPI</span>
          </div>
          <CardTitle className="text-xl font-extrabold tracking-tight">
            UPI-Easy SaaS Console
          </CardTitle>
          <CardDescription className="text-xs mt-1">
            Enterprise Merchant Multi-Tenant Gateway
          </CardDescription>
        </CardHeader>

        <CardContent className="p-0 space-y-4">
          {/* Server Status Indicator 
          <div className="p-2.5 rounded-xl bg-muted/50 border border-border flex items-center justify-between text-[11px] text-muted-foreground">
            <span className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
              Production API Gateway
            </span>
            <span className="font-mono text-emerald-600 dark:text-emerald-400 font-semibold">
              {pingMs ? `${pingMs}ms` : 'Active'}
            </span>
          </div> 
          */}

          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          {/* Custom Google Sign-In Button with seamless GSI tap handling */}
          <div className="relative w-full overflow-hidden rounded-lg group">
            <Button
              type="button"
              variant="outline"
              disabled={loading}
              onClick={() => promptOneTap(() => setShowManualToken(true))}
              className="w-full flex items-center justify-center gap-3 py-5 rounded-2xl border-border bg-card hover:bg-accent/40 font-semibold text-xs sm:text-sm shadow-sm transition-all duration-200 group-hover:border-brand-500/50">
              <IconGoogle className="w-5 h-5 flex-shrink-0" />
              <span>{loading ? 'Connecting to Google...' : 'Sign in with Google'}</span>
            </Button>

            {/* Transparent Google GSI overlay to trigger genuine iframe click on tap */}
            <div
              ref={buttonRef}
              title="Sign in with Google"
              className="absolute inset-0 opacity-[0.0001] cursor-pointer overflow-hidden z-10 flex items-center justify-center [&>div]:!w-full [&>div]:!h-full [&_iframe]:!w-full [&_iframe]:!h-full [&_iframe]:!cursor-pointer"
            />
          </div>

          <div className="pt-2 text-center">
            <button
              onClick={() => setShowManualToken(!showManualToken)}
              className="text-[11px] text-muted-foreground hover:text-foreground transition font-medium">
              {showManualToken ? 'Hide Direct Token Input' : 'Sign in with Google Token or API Key →'}
            </button>
          </div>

          {/* Manual Google ID Token Input */}
          {showManualToken && (
            <form onSubmit={handleManualSubmit} className="space-y-2 pt-1 animate-fade-in">
              <Input
                type="text"
                value={manualTokenInput}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => setManualTokenInput(e.target.value)}
                placeholder="Paste Google ID Token or JWT"
                className="font-mono text-xs"
              />
              <Button
                type="submit"
                variant="brand"
                disabled={loading}
                className="w-full text-xs font-bold">
                {loading ? 'Authenticating...' : 'Submit Token'}
              </Button>
            </form>
          )}
        </CardContent>

        <div className="mt-8 text-center text-[10px] text-muted-foreground flex items-center justify-center gap-1.5">
          <IconLock className="w-3 h-3" />
          <span>Protected by Google OAuth 2.0 & HMAC-SHA256 Encryption</span>
        </div>
      </Card>
    </div>
  );
}

export default LoginPage;
