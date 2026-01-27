import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    strictPort: true,
    host: true,
    allowedHosts: [".r9.cpolar.cn", ".r6.cpolar.top", ".r9.cpolar.cn", ".cpolar.cn", ".cpolar.top", "localhost", "127.0.0.1"],
    hmr: process.env.VITE_DISABLE_HMR === "1" ? false : true
  }
});
