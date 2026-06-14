import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Standard Vite + React setup. The dev server runs on 5173; the app talks only to the HTTP API.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173 },
});
