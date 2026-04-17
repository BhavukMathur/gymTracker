import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/coach": {
        target: "http://localhost:8090",
        changeOrigin: true,
      },
      /* If you change COACH_PORT in run-local.sh, update the port above to match. */
    },
  },
});
