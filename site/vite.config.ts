import { defineConfig } from "vite";

// Site GitHub Pages'te /SouNote/ alt yolunda yayınlanır (main dalı, /docs klasörü).
// Göreli base ("./") hem /SouNote/ altında hem de yerelde/özel alan adında çalışır.
export default defineConfig({
  base: "./",
  build: {
    outDir: "../docs",
    emptyOutDir: true,
    target: "es2020",
    assetsInlineLimit: 2048,
    chunkSizeWarningLimit: 700,
  },
  server: { host: true },
});
