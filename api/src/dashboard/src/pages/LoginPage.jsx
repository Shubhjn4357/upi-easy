// Authentication Page: Official Google Sign-In using shadcn/ui
function LoginPage({ onLoginSuccess, theme, onToggleTheme, pingMs }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [showManualToken, setShowManualToken] = useState(false);
  const [manualTokenInput, setManualTokenInput] = useState("");
  const googleBtnRef = useRef(null);

  // Initialize Google Identity Services (GSI)
  useEffect(() => {
    const initGoogleGsi = () => {
      if (window.google?.accounts?.id) {
        const clientId = window.GOOGLE_CLIENT_ID || "1029384756-mock.apps.googleusercontent.com";

        window.google.accounts.id.initialize({
          client_id: clientId,
          callback: async (response) => {
            if (response.credential) {
              await submitGoogleIdToken(response.credential);
            }
          },
          auto_select: false,
          cancel_on_tap_outside: true,
        });

        if (googleBtnRef.current) {
          googleBtnRef.current.innerHTML = "";
          window.google.accounts.id.renderButton(googleBtnRef.current, {
            theme: theme === "dark" ? "filled_black" : "outline",
            size: "large",
            shape: "pill",
            width: 320,
            text: "signin_with",
            logo_alignment: "center",
          });
        }
      }
    };

    const timer = setTimeout(initGoogleGsi, 200);
    return () => clearTimeout(timer);
  }, [theme]);

  // Submit verified Google ID token to backend
  const submitGoogleIdToken = async (idToken) => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch(`${window.location.origin}/api/v1/auth/google`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          idToken,
          deviceId: "web-saas-dashboard",
        }),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.error || data.message || "Google authentication failed");
      }

      onLoginSuccess(data.tokens.accessToken, data.user);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
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
          {theme === "dark" ? (
            <>
              <IconSun className="w-3.5 h-3.5 text-amber-400" />
              <span>Light</span>
            </>
          ) : (
            <>
              <IconMoon className="w-3.5 h-3.5 text-indigo-500" />
              <span>Dark</span>
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
          {/* Server Status Indicator */}
          <div className="p-2.5 rounded-xl bg-muted/50 border border-border flex items-center justify-between text-[11px] text-muted-foreground">
            <span className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
              Production API Gateway
            </span>
            <span className="font-mono text-emerald-600 dark:text-emerald-400 font-semibold">
              {pingMs ? `${pingMs}ms` : "Active"}
            </span>
          </div>

          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          {/* Official Google GSI Container */}
          <div className="flex justify-center min-h-[44px]">
            <div ref={googleBtnRef} className="flex justify-center w-full"></div>
          </div>

          {/* Fallback Direct Google One-Tap Trigger */}
          <Button
            variant="outline"
            onClick={() => {
              if (window.google?.accounts?.id) {
                window.google.accounts.id.prompt((notification) => {
                  if (notification.isNotDisplayed() || notification.isSkippedMoment()) {
                    setShowManualToken(true);
                  }
                });
              } else {
                setShowManualToken(true);
              }
            }}
            disabled={loading}
            className="w-full justify-center rounded-xl text-xs font-semibold">
            Prompt Google One-Tap
          </Button>

          <div className="pt-2 text-center">
            <button
              onClick={() => setShowManualToken(!showManualToken)}
              className="text-[11px] text-muted-foreground hover:text-foreground transition font-medium">
              {showManualToken ? "Hide Direct Token Input" : "Sign in with Google Token or API Key →"}
            </button>
          </div>

          {/* Manual Google ID Token Input */}
          {showManualToken && (
            <form
              onSubmit={async (e) => {
                e.preventDefault();
                if (!manualTokenInput.trim()) return;
                await submitGoogleIdToken(manualTokenInput.trim());
              }}
              className="space-y-2 pt-1 animate-fade-in">
              <Input
                type="text"
                value={manualTokenInput}
                onChange={(e) => setManualTokenInput(e.target.value)}
                placeholder="Paste Google ID Token or JWT"
                className="font-mono text-xs"
              />
              <Button
                type="submit"
                variant="brand"
                disabled={loading}
                className="w-full text-xs font-bold">
                {loading ? "Authenticating..." : "Submit Token"}
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

export { LoginPage };
export default LoginPage;
