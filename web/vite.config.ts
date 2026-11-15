import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  // Relative asset paths, so the site works under any GitHub Pages sub-path.
  base: "./",
  // data/site/ (puzzles + pictures) is served as-is at the site root: /puzzles/0001.json, /pictures/oblak.svg
  publicDir: "../data/site",
});
