// Environment Configuration Service (Strictly Typed)

export interface AppEnvironment {
  API_BASE_URL: string;
  GOOGLE_CLIENT_ID: string;
  DEVICE_ID: string;
  IS_PRODUCTION: boolean;
  IS_DEVELOPMENT: boolean;
}

function getEnvironment(): AppEnvironment {
  const metaEnv = typeof import.meta !== 'undefined' ? (import.meta as any).env || {} : {};
  const win = typeof window !== 'undefined' ? (window as any) : {};

  const apiBaseUrl =
    metaEnv.VITE_API_BASE_URL ||
    win.API_BASE_URL ||
    (typeof window !== 'undefined' ? window.location.origin : 'http://localhost:8080');

  const googleClientId =
    metaEnv.VITE_GOOGLE_CLIENT_ID ||
    win.GOOGLE_WEB_CLIENT_ID ||
    '332345540842-ks8bq4csr4lklkvv3tesgkig2b221m23.apps.googleusercontent.com';

  const deviceId =
    metaEnv.VITE_DEVICE_ID ||
    win.DEVICE_ID ||
    'web-saas-dashboard';

  return {
    API_BASE_URL: apiBaseUrl,
    GOOGLE_CLIENT_ID: googleClientId,
    DEVICE_ID: deviceId,
    IS_PRODUCTION: metaEnv.PROD || metaEnv.MODE === 'production',
    IS_DEVELOPMENT: metaEnv.DEV || metaEnv.MODE === 'development',
  };
}

export const ENV: AppEnvironment = getEnvironment();
export default ENV;
