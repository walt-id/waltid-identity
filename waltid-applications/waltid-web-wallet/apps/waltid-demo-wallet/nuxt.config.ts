import {defineNuxtConfig} from "nuxt/config";
import presetIcons from "@unocss/preset-icons";
import path from "path";

export default defineNuxtConfig({
    devtools: {enabled: true},
    srcDir: "src",

    modules: [
        "@vueuse/nuxt",
        ["@unocss/nuxt", {autoImport: false}],
        "@nuxtjs/i18n",
        "@nuxtjs/color-mode",
        "@vite-pwa/nuxt",
        "@sidebase/nuxt-auth",
        "@nuxt/content",
        "@pinia/nuxt",
        "nuxt-icon"
    ],

    build: {
        transpile: ["@headlessui/vue"]
    },

    auth: {
        // PTRID-753: this is a Nuxt module option, so it becomes a genuine runtimeConfig.public.auth.baseURL
        // key (see @sidebase/nuxt-auth's module.ts: `nuxt.options.runtimeConfig.public.auth = options`) --
        // resolving process.env HERE, in nuxt.config.ts, would bake whatever's set at `npm run build` time
        // (nothing, in our Docker build) as a permanent default; it would NOT pick up a value set later via
        // `docker run -e` / compose `environment:`. Leave this relative and let Nuxt's automatic runtime
        // override for nested public keys (NUXT_PUBLIC_AUTH_BASE_URL) set the absolute cross-origin value at
        // container start, same mechanism as runtimeConfig.public.walletApiBaseUrl below.
        baseURL: "/wallet-api/auth",

        provider: {
            type: "local",
            token: {
                maxAgeInSeconds: 60 * 60 * 24 * 30, // 30 days
                cookieName: 'auth.token',
                // PTRID-753: SameSite=None is required for a cross-origin fetch to carry this
                // cookie at all; browsers reject SameSite=None without Secure, so both change
                // together (@sidebase/nuxt-auth does not imply one from the other).
                sameSiteAttribute: 'none',
                secureCookieAttribute: true
            },

            endpoints: {
                signIn: {
                    // Legacy auth system: POST /login
                    // ktor-authnz system: POST /account/emailpass
                    path: process.env.NUXT_PUBLIC_AUTH_USE_KTORAUTHNZ === 'true'
                        ? '/account/emailpass'
                        : '/login',
                    method: 'post'
                },

                signOut: {path: '/logout', method: 'post'},
                signUp: {path: '/register', method: 'post'},
                getSession: {path: '/session', method: 'get'},
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
        injectManifest: {injectionPoint: undefined},
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
                    type: "image/png"
                },
                {
                    src: "/icons/waltid-icon-512x512.png",
                    sizes: "512x512",
                    type: "image/png"
                },
                {
                    src: "/icons/waltid-icon-512x512.png",
                    sizes: "512x512",
                    type: "image/png",
                    purpose: "any maskable"
                }
            ],
            shortcuts: [
                {
                    name: "Scan QR code",
                    short_name: "Scan QR",
                    url: "/wallet/scan-qr",
                    description: "Scan a QR code to receive/present credentials from/to a service."
                }
            ]
        },
        workbox: {
            navigateFallback: null,
            globPatterns: ["client/**/*.{js,css,ico,png,svg,webp,woff,woff2}"]
        },
        client: {
            installPrompt: true,
            // you don't need to include this: only for testing purposes
            // if enabling periodic sync for update use 1 hour or so (periodicSyncForUpdates: 3600)
            periodicSyncForUpdates: 20
        },
        devOptions: {
            enabled: true,
            type: "module"
        }
    },

    unocss: {
        uno: false,
        preflight: false,
        icons: true,
        presets: [
            presetIcons({
                scale: 1.2,
                extraProperties: {
                    display: "inline-block"
                }
            })
        ],
        safelist: ["i-twemoji-flag-us-outlying-islands", "i-twemoji-flag-turkey"]
    },

    typescript: {
        tsConfig: {
            compilerOptions: {
                strict: true,
                types: ["./type.d.ts"]
            }
        }
    },

    colorMode: {
        classSuffix: "",
        fallback: "light",
        storageKey: "color-mode"
    },

    vite: {
        logLevel: "info",
        resolve: {
            alias: {
                "@waltid-web-wallet": path.resolve(__dirname, "../../libs"),
            }
        },
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
            devWalletUrl: "https://wallet-dev.walt.id",
            // PTRID-753: absolute origin of wallet-api (e.g. "https://wallet-api.example.com"),
            // no trailing slash. Empty string preserves the old same-origin-proxy behavior
            // (relative /wallet-api/... paths) for any deployment that still needs it.
            walletApiBaseUrl: process.env.NUXT_PUBLIC_WALLET_API_BASE_URL || '',
        }
    },

    nitro: {
        compressPublicAssets: {
            gzip: true,
            brotli: false
        },
        devProxy: {
            "/wallet-api/": "http://localhost:7001/wallet-api"
        }
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
    compatibilityDate: "2024-07-26"
});
