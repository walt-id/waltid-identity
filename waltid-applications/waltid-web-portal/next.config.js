const nextConfig = {
  reactStrictMode: false,
  output: 'standalone',
  publicRuntimeConfig: {
    NEXT_PUBLIC_VC_REPO: process.env.NEXT_PUBLIC_VC_REPO ?? "https://credentials.walt.id/",
    NEXT_PUBLIC_ISSUER: process.env.NEXT_PUBLIC_ISSUER ?? "https://issuer.portal.walt.id",
    NEXT_PUBLIC_VERIFIER: process.env.NEXT_PUBLIC_VERIFIER ?? "https://verifier.portal.walt.id",
    NEXT_PUBLIC_VERIFIER2: process.env.NEXT_PUBLIC_VERIFIER2 ?? "https://verifier2.portal.walt.id",
    NEXT_PUBLIC_WALLET: process.env.NEXT_PUBLIC_WALLET ?? "https://wallet.walt.id"
  },
}

module.exports = nextConfig
