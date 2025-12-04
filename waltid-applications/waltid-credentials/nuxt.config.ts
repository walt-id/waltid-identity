// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
    srcDir: "src",

    devtools: { enabled: true },

    // Add compatibility date as recommended
    compatibilityDate: '2025-08-29',

    modules: [
        "@nuxt/content",
        ["@unocss/nuxt", { autoImport: false }],
        "@nuxt/icon", // Updated from deprecated nuxt-icon
        // "nuxt-monaco-editor", // not used right now
        "@nuxt/image",
        "@vueuse/nuxt",
        "nuxt-security"
    ],

    security: {
        corsHandler: {
            origin: "*",
            methods: "*"
        },
        rateLimiter: false
    },

    // Add vite config to handle global issues
    vite: {
        define: {
            global: 'globalThis'
        }
    },

    content: {
        highlight: {
            theme: "github-dark",
            preload: ["json", "kotlin", "http", "js", "ts", "md", "shell"]
        }
    },

    router: {
        options: {
            scrollBehaviorType: "smooth"
        }
    }
});
