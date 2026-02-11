<div align="center">
 <h1>Credential Issuance & Verification Portal</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Web application that showcases the issuance and verification of W3C Verifiable Credentials</p>

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

This web portal integrates four core walt.id services to provide a complete credential lifecycle:

- **Credentials API** (`NEXT_PUBLIC_VC_REPO`): Serves credential schemas and templates
- **Issuer API** (`NEXT_PUBLIC_ISSUER`): Issues verifiable credentials to wallets (OpenID4VCI Draft 13)
- **Verifier API** (`NEXT_PUBLIC_VERIFIER`): Requests and verifies credentials from wallets (OpenID4VP Draft 20)
- **Wallet** (`NEXT_PUBLIC_WALLET`): End-user wallet for receiving and presenting credentials

The portal acts as a frontend that orchestrates these services to demonstrate credential issuance and verification flows.

## Main Purpose

This portal enables users to:

- **Issue Credentials**: Create and send credential offers to wallets using the OpenID4VCI protocol
- **Verify Credentials**: Request credential presentations from wallets using the OpenID4VP protocol
- **Browse Credentials**: View available credential types from a credentials repository
- **Configure Settings**: Customize credential formats, DID methods, and verification policies
- **Test Flows**: Test end-to-end credential issuance and verification workflows

## Try It Out

**Live Demo**: Visit the deployed portal at [portal.demo.walt.id](https://portal.demo.walt.id) to see it in action.

**Quickstart Guide**: Follow our [5-minute quickstart guide](https://docs.walt.id/community-stack/home/quickstart-5-min) for a walkthrough of the portal's features.

## Key Concepts

### Credential Issuance Flow

1. **Select Credential**: Choose a credential type from the available list
2. **Configure Settings**: Optionally configure format, DID method, and authentication
3. **Generate Offer**: Create an OpenID4VCI credential offer
4. **Present Offer**: Display QR code or direct link for wallet to receive credential
5. **Wallet Receives**: User accepts credential in their wallet

### Credential Verification Flow

1. **Select Credential**: Choose the credential type to verify
2. **Configure Policies**: Select verification policies (signature, expiration, schema, etc.)
3. **Generate Request**: Create an OpenID4VP authorization request
4. **Present Request**: Display QR code or direct link for wallet to present credential
5. **Verify Result**: Display verification results after credential presentation

## Assumptions and Dependencies

### Platform Support

- **Web Application**: Built with Next.js (React framework)
- **Modern Browsers**: Requires modern browser with JavaScript enabled
- **Node.js**: Requires Node.js for local development

### Required Dependency Services

The portal requires the following services to be running and accessible:

- **Credentials Repository**: Service that provides a list of available credential types and their schemas
  - Configured via `NEXT_PUBLIC_VC_REPO` environment variable
  - Must expose `/api/list` and `/api/vc/{id}` endpoints

- **Issuer API**: Service for issuing credentials (OpenID4VCI)
  - Configured via `NEXT_PUBLIC_ISSUER` environment variable
  - Must implement OpenID4VCI protocol endpoints

- **Verifier API**: Service for verifying credentials (OpenID4VP)
  - Configured via `NEXT_PUBLIC_VERIFIER` environment variable
  - Must implement OpenID4VP protocol endpoints

- **Wallet API**: Service for wallet interactions
  - Configured via `NEXT_PUBLIC_WALLET` environment variable
  - Used for direct wallet integration (optional)

### Dependencies

- **Next.js 14**: React framework for web applications
- **React 18**: UI library
- **Tailwind CSS**: Utility-first CSS framework
- **Axios**: HTTP client for API communication
- **QR Code Library**: For generating QR codes

## Usage

### Prerequisites

- Node.js 18+ and pnpm (or npm/yarn)
- Access to required dependency services (see above)

### Environment Configuration

The portal requires environment variables to connect to dependency services. Create a `.env` or `.env.local` file:

```text
NEXT_PUBLIC_VC_REPO=https://credentials.walt.id
NEXT_PUBLIC_ISSUER=https://issuer.portal.walt.id
NEXT_PUBLIC_VERIFIER=https://verifier.portal.walt.id
NEXT_PUBLIC_WALLET=https://wallet.walt.id
```

### Development

1. **Install dependencies**:

```bash
pnpm install
```

2. **Run development server**:

```bash
pnpm dev
```

The application will be available at `http://localhost:3000`.

### Production Build

Build the application for production:

```bash
pnpm install
pnpm build
pnpm start
```

### Docker

Build and run using Docker:

```bash
docker build -t waltid/portal -f waltid-web-portal/Dockerfile .
docker run -p 7102:7102 -i -t waltid/portal
```

**Note**: When using Docker, ensure environment variables are set via `-e` flags or an `.env` file.

## Adding New Credentials

To add support for new credential types:

1. **Add credential schema** to the Credentials API repository. Learn more [here](https://github.com/walt-id/waltid-identity/tree/main/waltid-applications/waltid-credentials).
2. **Update issuer metadata** to include the new credential type in supported credentials. Learn more [here](https://docs.walt.id/community-stack/issuer/api/configurations/config-files/credential-issuer-metadata).
3. The portal will automatically discover and display new credentials from the configured endpoints.

Both the credential repository and issuer metadata must be updated for credentials to appear in the issuance flow.

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)


## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>
