// Environment Configuration Service (Strictly Typed)

export interface AppEnvironment {
  API_BASE_URL: string;
  GOOGLE_CLIENT_ID: string;
  DEVICE_ID: string;
  IS_PRODUCTION: boolean;
  IS_DEVELOPMENT: boolean;
}

interface CustomWindow {
  API_BASE_URL?: string;
  GOOGLE_CLIENT_ID?: string;
  location?: Location;
}

function getEnvironment(): AppEnvironment {
  const metaEnv = typeof import.meta !== 'undefined' ? (import.meta as { env?: Record<string, string> }).env || {} : {};
  const win = (typeof window !== 'undefined' ? window : {}) as unknown as CustomWindow;

  const isBrowser = typeof window !== 'undefined';

  let apiBaseUrl = '';
  if (win.API_BASE_URL) {
    apiBaseUrl = win.API_BASE_URL;
  } else if (isBrowser && window.location?.origin && !window.location.origin.includes('localhost:5173')) {
    // Production Cloudflare Workers or remote deployment: use active browser origin
    apiBaseUrl = window.location.origin;
  } else if (metaEnv.VITE_API_BASE_URL) {
    apiBaseUrl = metaEnv.VITE_API_BASE_URL;
  } else if (isBrowser && window.location?.origin) {
    apiBaseUrl = window.location.origin;
  } else {
    apiBaseUrl = 'http://localhost:8080';
  }

  apiBaseUrl = apiBaseUrl.replace(/\/+$/, '');

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
