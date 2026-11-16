import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// The dev server forwards /api to the Spring Boot app, so the browser needs no CORS setup.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { "/api": process.env.API_URL ?? "http://localhost:8080" },
  },
});
