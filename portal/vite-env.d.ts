/// <reference types="vite/client" />

// Typed environment variables read via import.meta.env (see .env.example for the values to set).
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_FIREBASE_API_KEY: string;
  readonly VITE_FIREBASE_AUTH_DOMAIN: string;
  readonly VITE_FIREBASE_PROJECT_ID: string;
  readonly VITE_FIREBASE_APP_ID: string;
  readonly VITE_FIREBASE_AUTH_EMULATOR?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
