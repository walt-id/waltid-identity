import { defineNuxtConfig } from 'nuxt/config'

export default defineNuxtConfig({
  devtools: { enabled: true },
  ssr: false,
  compatibilityDate: '2024-07-26',

  modules: ['@nuxtjs/tailwindcss'],

  css: ['~/assets/css/main.css'],

  runtimeConfig: {
    public: {
      issuerBase: process.env.NUXT_PUBLIC_ISSUER_BASE || 'http://localhost:7002',
      verifierBase: process.env.NUXT_PUBLIC_VERIFIER_BASE || 'http://localhost:7003',
      walletUrl: process.env.NUXT_PUBLIC_WALLET_URL || 'http://localhost:7101',
    },
  },

  typescript: {
    strict: true,
  },
})
