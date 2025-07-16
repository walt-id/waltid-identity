import { defineNuxtConfig } from "nuxt/config";
import presetIcons from "@unocss/preset-icons";
import path from "path";

export default defineNuxtConfig({
  devtools: { enabled: true },
  srcDir: "src",

  devServer: {
    port: 7104,
  },

  modules: [
    "@vueuse/nuxt",
    ["@unocss/nuxt", { autoImport: false }],
    "@nuxtjs/i18n",
    "@nuxtjs/color-mode",
    "@vite-pwa/nuxt",
    "@sidebase/nuxt-auth",
    "@nuxt/content",
    "@pinia/nuxt",
    "nuxt-icon",
  ],

  build: {
    transpile: ["@headlessui/vue"],
  },

  auth: {
    baseURL: "/wallet-api/auth",

    provider: {
      type: "local",
      token: {
        maxAgeInSeconds: 60 * 60 * 24 * 30, // 30 days
      },

      pages: {
        login: "/login",
      },
    },

    globalAppMiddleware: {
      isEnabled: true,
    },
  },

  pwa: {
    registerWebManifestInRouteRules: true,

    srcDir: "public/sw",
    filename: "worker.js",

    strategies: "injectManifest",
    injectRegister: "script",
    injectManifest: { injectionPoint: undefined },
    registerType: "autoUpdate",
    // notification-worker.js
    manifest: {
      name: "walt.id wallet",
      short_name: "walt.id",
      display: "standalone",
      theme_color: "#0573f0",
      icons: [
        {
          src: "/icons/android-icon-36x36.png",
          sizes: "36x36",
          type: "image/png",
        },
        {
          src: "/icons/android-icon-48x48.png",
          sizes: "48x48",
          type: "image/png",
        },
        {
          src: "/icons/android-icon-72x72.png",
          sizes: "72x72",
          type: "image/png",
        },
        {
          src: "/icons/android-icon-96x96.png",
          sizes: "96x96",
          type: "image/png",
        },
        {
          src: "/icons/android-icon-144x144.png",
          sizes: "144x144",
          type: "image/png",
        },
        {
          src: "/icons/waltid-icon-192x192.png",
          sizes: "192x192",
          type: "image/png",
        },
        {
          src: "/icons/waltid-icon-512x512.png",
          sizes: "512x512",
          type: "image/png",
        },
        {
          src: "/icons/waltid-icon-512x512.png",
          sizes: "512x512",
          type: "image/png",
          purpose: "any maskable",
        },
      ],
      shortcuts: [
        {
          name: "Scan QR code",
          short_name: "Scan QR",
          url: "/wallet/scan-qr",
          description:
            "Scan a QR code to receive/present credentials from/to a service.",
        },
      ],
    },
    workbox: {
      navigateFallback: null,
      globPatterns: ["client/**/*.{js,css,ico,png,svg,webp,woff,woff2}"],
    },
    client: {
      installPrompt: true,
      // you don't need to include this: only for testing purposes
      // if enabling periodic sync for update use 1 hour or so (periodicSyncForUpdates: 3600)
      periodicSyncForUpdates: 20,
    },
    devOptions: {
      enabled: true,
      type: "module",
    },
  },

  unocss: {
    uno: false,
    preflight: false,
    icons: true,
    presets: [
      presetIcons({
        scale: 1.2,
        extraProperties: {
          display: "inline-block",
        },
      }),
    ],
    safelist: ["i-twemoji-flag-us-outlying-islands", "i-twemoji-flag-turkey"],
  },

  typescript: {
    tsConfig: {
      compilerOptions: {
        strict: true,
        types: ["./type.d.ts"],
      },
    },
  },

  colorMode: {
    classSuffix: "",
    fallback: "light",
    storageKey: "color-mode",
  },

  vite: {
    logLevel: "info",
    resolve: {
      alias: {
        "@waltid-web-wallet": path.resolve(__dirname, "../../libs"),
      },
    },
    server: {
      allowedHosts: true
    }
    /*server: {
            proxy: {
                '/api': {
                    target: 'http://localhost:4545'
                }
            }
        }*/
  },

  runtimeConfig: {
    public: {
      projectId: process.env.ProjectId,
      issuerCallbackUrl: "http://localhost:7100",
      credentialsRepositoryUrl: "http://localhost:3000",
      demoWalletUrl: "https://wallet-dev.walt.id",
    },
  },

  nitro: {
    compressPublicAssets: {
      gzip: true,
      brotli: false,
    },
    devProxy: {
      "/wallet-api/": "http://localhost:7001/wallet-api",
    },
  },

  // i18n: {
  //     lazy: true,
  //     langDir: 'locales',  // need `lang` dir on `admin`
  //     defaultLocale: "en-US",
  //     detectBrowserLanguage: false,
  //     locales: [
  //         {
  //             code: 'en',
  //             file: 'en-US.json',
  //         },
  //         {
  //             code: 'tr',
  //             file: 'tr-TR.json',
  //         },
  //     ]
  // }
  //proxy: [ 'http://localhost:4545/api' ]

  ssr: false,
  compatibilityDate: "2024-07-26",
});
