const nextConfig = {
  reactStrictMode: false,
  output: 'standalone',
  publicRuntimeConfig: {
    NEXT_PUBLIC_VC_REPO: process.env.NEXT_PUBLIC_VC_REPO ?? "https://credentials.walt.id/",
    NEXT_PUBLIC_ISSUER: process.env.NEXT_PUBLIC_ISSUER ?? "http://localhost:7002",
    NEXT_PUBLIC_VERIFIER: process.env.NEXT_PUBLIC_VERIFIER ?? "https://verifier.portal.walt.id",
    NEXT_PUBLIC_WALLET: process.env.NEXT_PUBLIC_WALLET ?? "https://wallet.walt.id"
  },
}

module.exports = nextConfig
