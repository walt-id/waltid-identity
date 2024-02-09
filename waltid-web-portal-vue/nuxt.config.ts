// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
    srcDir: "src",
    devtools: { enabled: true },
    modules: [
        ['@unocss/nuxt', { autoImport: false }],
        "nuxt-icon",
        "nuxt-monaco-editor",
        "nuxt-headlessui",
        "@formkit/auto-animate/nuxt"
    ],
    runtimeConfig: {
        public: {
            credentialRepository: "", // overwritten with NUXT_PUBLIC_CREDENTIAL_REPOSITORY
            issuer: "", // overwritten with NUXT_PUBLIC_ISSUER
            verifier: "", // overwritten with NUXT_PUBLIC_VERIFIER
            webWalletPrefix: "", // overwritten with NUXT_PUBLIC_WEB_WALLET_PREFIX
        }
    }
});
