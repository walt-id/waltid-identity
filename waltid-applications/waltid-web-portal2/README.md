<div align="center">
 <h1>Credential Issuance & Verification Portal</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Web application for interactive Verifiable Credential issuance and verification flows</p>

  <a href="https://walt.id/community">
  <img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
  </a>
  <a href="https://www.linkedin.com/company/walt-id/">
  <img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
  </a>

  <h2>Status</h2>
  <p align="center">
    <img src="https://img.shields.io/badge/🟢%20Actively%20Maintained-success?style=for-the-badge&logo=check-circle" alt="Status: Actively Maintained" />
    <br/>
    <em>This project is being actively maintained by the development team at walt.id.<br />Regular updates, bug fixes, and new features are being added.</em>
  </p>
</div>

## Overview

This portal integrates two core walt.id services to provide an interactive credential lifecycle demo:

- **Issuer API2** (`NUXT_PUBLIC_ISSUER_BASE`): Issues verifiable credentials to wallets (OpenID4VCI)
- **Verifier API2** (`NUXT_PUBLIC_VERIFIER_BASE`): Requests and verifies credentials from wallets (OpenID4VP)
- **Wallet** (`NUXT_PUBLIC_WALLET_URL`): End-user wallet for receiving and presenting credentials

The portal acts as a frontend that orchestrates these services to demonstrate credential issuance and verification flows, with real-time event streaming via SSE.

## Main Purpose

This portal enables users to:

- **Issue Credentials**: Create credential offers using custom JSON payloads and send them to wallets via OpenID4VCI (QR / deep link or Digital Credentials API)
- **Select a Profile**: Choose an issuance profile from a dropdown (loaded from `/issuer2/profiles`). The selection overrides the `profileId` in the payload, and the full profile definition (`/issuer2/profiles/{profileId}`) is available in a collapsible JSON viewer
- **Verify Credentials**: Request credential presentations from wallets using OpenID4VP (including Digital Credentials API flows)
- **Load Examples**: Auto-populate payloads from OpenAPI/Swagger specs served by the backend
- **Read the Docs**: Jump to the relevant Issuer/Verifier API docs from each tab
- **Monitor Events**: Watch real-time protocol events stream in as wallets interact with the portal
- **Test Flows**: Test end-to-end credential issuance and verification workflows

## Try It Out

**Live Demo**: Visit [portal2.demo.walt.id](https://portal2.demo.walt.id) to see it in action.

**Quickstart Guide**: Follow our [5-minute quickstart guide](https://docs.walt.id/community-stack/home/quickstart-5-min) for a walkthrough of the credential flows.

## Key Concepts

### Credential Issuance Flow

1. **Edit Payload**: Select a Swagger example or write a custom JSON credential offer payload
2. **Create Offer**: Portal calls the Issuer API to generate a credential offer
3. **Deliver to wallet** using one of:
   - **QR / deep link**: A QR code is displayed for the wallet to scan (enter PIN if required)
   - **Digital Credentials API**: The portal enriches the offer with issuer/AS metadata and calls `navigator.credentials.create` with protocol `openid4vci-v1` to engage a wallet (Chrome may show a proximity QR). Browser create() handoff success/failure is ignored; the result log follows issuer OpenID4VCI SSE only. The issuer stays standard OpenID4VCI — there is no issuer-side DC API protocol mode.
4. **Monitor Events**: Real-time SSE events show each issuer step (`requested_token`, `generated_mdoc`, `issuance_status`, etc.)
5. **Wallet Receives**: User accepts the credential in their wallet

#### Digital Credentials API issuance requirements

DC API issuance is experimental (Chrome origin trial). Official setup guide:
[Digital Credentials API for credential issuance](https://developer.chrome.com/blog/digital-credentials-api-143-issuance-ot).

To try it:

- Chrome 143+ on desktop and/or Android
- Enable `chrome://flags/#web-identity-digital-credentials-creation`
- Google Play services 24.0+ on Android, plus a compatible wallet (for example CMWallet)
- Prefer HTTPS (or localhost) so the page is a secure context
- Hosted demos may also need a Chrome origin-trial token for the site origin

If DC API is unavailable, the portal disables that delivery option and links to the docs above — use QR / deep link instead. The issuer API is unchanged.

### Credential Verification Flow

1. **Edit Payload**: Select a Swagger example or write a custom JSON verification request
2. **Create Session**: Portal calls the Verifier API to generate an authorization request
3. **Scan QR Code** or complete a **Digital Credentials API** verification session
4. **Monitor Events**: Real-time SSE events show each step of the presentation exchange
5. **View Results**: Verified claims are extracted and displayed after a successful presentation

## Usage

### Environment Configuration

Create a `.env` file to connect to dependency services:

```text
NUXT_PUBLIC_ISSUER_BASE=http://localhost:7005
NUXT_PUBLIC_VERIFIER_BASE=http://localhost:7004
NUXT_PUBLIC_WALLET_URL=http://localhost:7101
NUXT_PUBLIC_VERIFIER_KEY_JWK=
NUXT_PUBLIC_VERIFIER_X5C=
NUXT_PUBLIC_VERIFIER_CLIENT_ID=
```

The optional verifier values pre-populate the advanced verification security section. `NUXT_PUBLIC_VERIFIER_KEY_JWK` accepts the verifier signing key as a raw JWK or as `{ "type": "jwk", "jwk": ... }`, `NUXT_PUBLIC_VERIFIER_X5C` accepts a JSON array, comma-separated list, or newline-separated list of base64 DER certificates, and `NUXT_PUBLIC_VERIFIER_CLIENT_ID` accepts any supported client ID such as `x509_hash:<hash>`, `x509_san_dns:<dns>`, `redirect_uri:<url>`, `decentralized_identifier:<did>`, `verifier_attestation:<value>`, or an unprefixed pre-registered ID.

### Development

1. **Install dependencies**:

```bash
npm install
```

2. **Run development server**:

```bash
npm run dev
```

The application will be available at `http://localhost:3000`.

### Production Build

Build the application for production:

```bash
npm install
npm run build
npm start
```

### Docker

Build and run using Docker:

```bash
docker build -t waltid/portal2 -f waltid-applications/waltid-web-portal2/Dockerfile .
docker run -p 3000:3000 -i -t waltid/portal2
```

**Note**: When using Docker, ensure environment variables are set via `-e` flags or an `.env` file.

## Join the community

- Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
- Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
- Find more in-depth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>
