import { defineNuxtConfig } from "nuxt/config";

export default defineNuxtConfig({
  devtools: { enabled: true },
  ssr: false,
  compatibilityDate: "2024-07-26",
  app: {
    head: {
      link: [{ rel: "icon", href: "/favicon.ico" }],
    },
  },

  modules: ["@nuxtjs/tailwindcss"],

  css: ["~/assets/css/main.css"],

  runtimeConfig: {
    public: {
      issuerBase:
        process.env.NUXT_PUBLIC_ISSUER_BASE || "http://localhost:7005",
      verifierBase:
        process.env.NUXT_PUBLIC_VERIFIER_BASE || "http://localhost:7004",
      walletUrl: process.env.NUXT_PUBLIC_WALLET_URL || "http://localhost:7101",
      verifierKeyJwk: process.env.NUXT_PUBLIC_VERIFIER_KEY_JWK || "",
      verifierX5c: process.env.NUXT_PUBLIC_VERIFIER_X5C || "",
      verifierClientId: process.env.NUXT_PUBLIC_VERIFIER_CLIENT_ID || "",
    },
  },

  typescript: {
    strict: true,
  },
});
