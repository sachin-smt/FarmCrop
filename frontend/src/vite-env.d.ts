/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Absolute backend origin for production builds. Empty in development (the Vite proxy is used). */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
