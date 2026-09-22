// Global browser window augmentations for Google Identity Services & runtime environment

export interface GoogleIdCallbackResponse {
  credential?: string;
  select_by?: string;
}

export interface GoogleIdInitializeOptions {
  client_id: string;
  callback: (response: GoogleIdCallbackResponse) => void | Promise<void>;
  auto_select?: boolean;
  cancel_on_tap_outside?: boolean;
}

export interface GoogleIdRenderButtonOptions {
  type?: 'standard' | 'icon';
  theme?: 'outline' | 'filled_blue' | 'filled_black';
  size?: 'large' | 'medium' | 'small';
  text?: 'signin_with' | 'signup_with' | 'continue_with' | 'signin';
  shape?: 'rectangular' | 'pill' | 'circle' | 'square';
  logo_alignment?: 'left' | 'center';
  width?: number | string;
  locale?: string;
  click_listener?: () => void;
}

export interface GoogleAccountsId {
  initialize: (options: GoogleIdInitializeOptions) => void;
  renderButton: (parent: HTMLElement, options: GoogleIdRenderButtonOptions) => void;
  prompt: (momentListener?: (notification: { isNotDisplayed: () => boolean; isSkippedMoment: () => boolean }) => void) => void;
  disableAutoSelect?: () => void;
  revoke?: (hint: string, done: () => void) => void;
}

export interface GoogleNamespace {
  accounts: {
    id: GoogleAccountsId;
  };
}

declare global {
  interface Window {
    google?: GoogleNamespace;
    GOOGLE_WEB_CLIENT_ID?: string;
    API_BASE_URL?: string;
    DEVICE_ID?: string;
  }
}

export {};
