// Firebase setup for Auth only (Email/Password), configured from VITE_ env vars; all data goes through the /portal HTTP API.
import { initializeApp } from "firebase/app";
import { getAuth, connectAuthEmulator } from "firebase/auth";

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
};

const app = initializeApp(firebaseConfig);

// The Auth instance the whole app uses.
export const auth = getAuth(app);

// For local dev: point Auth at the emulator when VITE_FIREBASE_AUTH_EMULATOR is set.
const emulator = import.meta.env.VITE_FIREBASE_AUTH_EMULATOR;
if (emulator) {
  connectAuthEmulator(auth, emulator, { disableWarnings: true });
}
