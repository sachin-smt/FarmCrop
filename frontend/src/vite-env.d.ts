/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Absolute backend origin for production builds. Empty in development (the Vite proxy is used). */
  readonly VITE_API_BASE_URL?: string;
  /** Firebase web-app config for Google and phone (OTP) sign-in. Public identifiers, not secrets. */
  readonly VITE_FIREBASE_API_KEY?: string;
  readonly VITE_FIREBASE_AUTH_DOMAIN?: string;
  readonly VITE_FIREBASE_PROJECT_ID?: string;
  readonly VITE_FIREBASE_APP_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
