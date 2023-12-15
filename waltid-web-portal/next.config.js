/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: false,
  //TODO: fix env
  publicRuntimeConfig: {
    NEXT_PUBLIC_VC_REPO: process.env.NEXT_PUBLIC_VC_REPO ?? "https://vc-repo.walt-test.cloud",
    NEXT_PUBLIC_ISSUER: process.env.NEXT_PUBLIC_ISSUER ?? "https://issuer.portal.walt.id",
    NEXT_PUBLIC_VERIFIER: process.env.NEXT_PUBLIC_VERIFIER ?? "https://verifier.portal.walt.id",
    NEXT_PUBLIC_WALLET: process.env.NEXT_PUBLIC_WALLET ?? "https://wallet.walt.id",
    NEXT_PUBLIC_HOST_URL: process.env.NEXT_PUBLIC_HOST_URL ?? "http://localhost:3000"
  },
}

module.exports = nextConfig
