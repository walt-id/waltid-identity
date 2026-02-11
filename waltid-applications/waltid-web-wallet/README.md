<div align="center">
 <h1>Web Wallet (Frontend)</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>White-label web wallet solution for Verifiable Credentials and NFTs</p>

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

## What This Application Contains

`waltid-web-wallet` is a white-label web wallet frontend built with Nuxt.js that provides a complete user interface for managing Verifiable Credentials and NFTs. It offers two variants: a **Demo Wallet** for end-user demonstrations and a **Dev Wallet** for development and testing with additional features.

## Main Purpose

This wallet enables users to:

- **Receive Credentials**: Accept credential offers via OpenID4VCI (Draft 13) protocol
- **Store Credentials**: Securely store credentials in various formats (W3C, SD-JWT, mdoc)
- **Present Credentials**: Share credentials via OpenID4VP (Draft 20) protocol
- **Manage DIDs**: Create and manage Decentralized Identifiers across multiple ecosystems
- **Manage Keys**: Generate, import, and export cryptographic keys
- **View NFTs**: Display NFTs from multiple blockchain ecosystems
- **Cross-Device Support**: Use QR codes for cross-device credential exchange
- **Browser Notifications**: Leverage browser notification system for asynchronous flows

## Try It Out

**Live Demos**: 
- **Demo Wallet**: Visit [wallet.demo.walt.id](https://wallet.demo.walt.id) for the production demo
- **Dev Wallet**: Visit [wallet-dev.demo.walt.id](https://wallet-dev.demo.walt.id) for the development version

**Intro Video**: [Watch the intro video](https://youtu.be/HW9CNFmRFlI) to learn about features and see a demo.

**Docs**: [Learn more about our Wallet API](https://docs.walt.id/community-stack/wallet/getting-started)

## Wallet Variants

The web wallet comes in two variants:

### Demo Wallet (`waltid-demo-wallet`)

- **Purpose**: Production-ready wallet for end-user demonstrations
- **Port**: Runs on port 7101 (local development)
- **Features**: Streamlined UI focused on core credential management
- **Use Case**: Public-facing demonstrations and user testing

### Dev Wallet (`waltid-dev-wallet`)

- **Purpose**: Development wallet with extended features
- **Port**: Runs on port 7104 (local development)
- **Features**: Additional development tools, testing features, and advanced settings
- **Use Case**: Development, testing, and integration work

Both wallets share the same core functionality but differ in UI complexity and available features.

## Key Concepts

### Credential Formats

The wallet supports multiple credential formats:

- **W3C Verifiable Credentials**: Standard W3C VC format (v1.1 and v2.0)
- **SD-JWT VC**: Selective Disclosure JWT Verifiable Credentials
- **mdoc**: ISO/IEC 18013-5 mobile document credentials
- **JWT**: JSON Web Token format credentials

### Protocol Support

- **OpenID4VCI (Draft 13)**: For receiving credentials from issuers
- **OpenID4VP (Draft 20)**: For presenting credentials to verifiers
- **SIOP (Self-Issued OpenID Provider)**: For self-issued credentials

### DID Methods

Supports multiple DID methods including:
- `did:key`, `did:jwk`, `did:web`, `did:ebsi`, `did:cheqd`, `did:iota`, and more

### Exchange Flows

- **Synchronous**: Same-device and cross-device flows using QR codes
- **Asynchronous**: Browser notification system for background credential exchange

## Assumptions and Dependencies

### Platform Support

- **Web Application**: Built with Nuxt.js (Vue.js framework)
- **PWA Support**: Progressive Web App with offline capabilities
- **Modern Browsers**: Requires modern browser with JavaScript enabled
- **Node.js**: Requires Node.js for local development

### Required Dependency Services

The wallet frontend requires the following services to be running and accessible:

- **Wallet API**: Backend service for wallet operations
  - Handles credential storage, DID management, key management
  - Implements OpenID4VCI and OpenID4VP protocol endpoints
  - Provides authentication and session management
  - Located in `/waltid-wallet-api` folder

- **Credentials Repository**: Service that provides credential schemas and metadata
  - Used for displaying credential information and manifests
  - Provides credential type definitions

### Dependencies

- **Nuxt.js**: Vue.js framework for web applications
- **Vue 3**: Progressive JavaScript framework
- **UnoCSS**: Atomic CSS engine
- **PWA Module**: Progressive Web App support
- **Pinia**: State management
- **VueUse**: Composition utilities

## Usage

### Prerequisites

- Node.js 18+ and npm/pnpm
- Access to Wallet API service
- Access to Credentials Repository (optional, for enhanced features)

### Running with Docker

From the repository root, run all services including the wallet frontend:

```bash
cd docker-compose && docker compose up
```

This will start:
- Demo wallet at [localhost:7101](http://localhost:7101)
- Dev wallet at [localhost:7104](http://localhost:7104)
- Wallet API with Swagger at `/swagger` endpoint

### Building Docker Images

Update the containers by building the images:

```bash
docker build -f waltid-applications/waltid-web-wallet/apps/waltid-demo-wallet/Dockerfile -t waltid/waltid-demo-wallet .
docker build -f waltid-applications/waltid-web-wallet/apps/waltid-dev-wallet/Dockerfile -t waltid/waltid-dev-wallet .
```

### Development

1. **Install dependencies**:

```bash
npm install
```

2. **Run demo wallet**:

```bash
cd apps/waltid-demo-wallet && npm run dev
```

3. **Run dev wallet**:

```bash
cd apps/waltid-dev-wallet && npm run dev
```

**Note**: The wallet frontend requires the Wallet API backend to be running. Configure the API endpoint in the Nuxt configuration or via environment variables.

## Features

### Verifiable Credentials

- **Receive Credentials**: Accept credential offers via QR code or direct link
- **Store Credentials**: Securely store credentials in multiple formats
- **View Credentials**: Display credential details with issuer information
- **Present Credentials**: Share credentials with verifiers via QR code or direct link
- **Credential Disclosure**: Selective disclosure for SD-JWT credentials
- **Credential Validation**: Check credential expiration and validity

### DID Management

- **Create DIDs**: Generate new DIDs using various methods
- **List DIDs**: View all DIDs associated with the wallet
- **Delete DIDs**: Remove DIDs from the wallet
- **DID Resolution**: Resolve DID documents

### Key Management

- **Generate Keys**: Create new cryptographic keys
- **Import Keys**: Import existing keys
- **Export Keys**: Export keys in various formats
- **Key Algorithms**: Support for Ed25519, secp256k1, secp256r1, RSA

### NFT Support

- **Multi-Chain NFTs**: View NFTs from Ethereum, Near, Flow, Tezos, Algorand, and more
- **Unified Interface**: Single interface for NFTs across ecosystems
- **NFT Metadata**: Display NFT artwork and metadata

### User Experience

- **Responsive Design**: Works on desktop and mobile devices
- **PWA Support**: Installable as a Progressive Web App
- **QR Code Scanning**: Scan QR codes for credential exchange
- **Browser Notifications**: Receive notifications for credential offers and requests
- **Multi-Language**: Internationalization support


## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)


## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>
