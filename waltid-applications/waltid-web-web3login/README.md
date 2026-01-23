<div align="center">
    <h1>walt.id Web3 Login</h1>
    <span>by </span><a href="https://walt.id">walt.id</a>
    <p>Reference implementation for Web3 wallet-based authentication using signature verification</p>
    <a href="https://walt.id/community">
    <img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
    </a>
    <a href="https://www.linkedin.com/company/walt-id/">
    <img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
    </a>
    <h2>Status</h2>
    <p align="center">
        <img src="https://img.shields.io/badge/🟡%20Unmaintained-yellow?style=for-the-badge&logo=warning" alt="Status: Unmaintained" />
        <br/>
        <em>This project is not actively maintained. Certain features may be outdated or not working as expected.<br />We encourage users to contribute to the project to help keep it up to date.</em>
    </p>
</div>

## What This Application Contains

`waltid-web-web3login` is a Nuxt 3 web application demonstrating Web3 wallet-based authentication. It provides a reference implementation for authenticating users using their Web3 wallets (e.g., MetaMask) through cryptographic signature verification.

## Main Purpose

This application demonstrates how to implement Web3-based login in a web application by:

- **Challenge-Response Authentication**: Requesting a cryptographic nonce from the server
- **Wallet Signature**: Having users sign the challenge with their Web3 wallet
- **Signature Verification**: Verifying the signature using ECDSA signature recovery
- **Address-Based Authentication**: Using the recovered wallet address as the user identifier

## Key Concepts

### Web3 Authentication Flow

1. **Nonce Request**: Client requests a challenge/nonce from the server
2. **Wallet Signing**: User signs the challenge using their Web3 wallet (MetaMask, WalletConnect, etc.)
3. **Signature Submission**: Client submits the signature and wallet address to the server
4. **Signature Verification**: Server recovers the public key from the signature and verifies it matches the provided address
5. **Authentication**: Upon successful verification, the user is authenticated using their wallet address

### Signature Verification

The authentication uses ECDSA signature recovery:
- The server generates a time-limited nonce (challenge)
- The user signs the challenge with their wallet's private key
- The server recovers the public key from the signature
- The recovered address is compared with the provided address
- If they match, authentication succeeds

### Account Registration

Web3 authentication supports automatic registration:
- If a wallet address is not yet registered, it can be automatically created
- The wallet address serves as the unique identifier for the account
- No passwords or traditional credentials are required

## Assumptions and Dependencies

### Platform Support

- **Web Application**: Built with Nuxt 3 (Vue.js framework)
- **Browser Support**: Requires Web3 wallet browser extension (MetaMask, WalletConnect, etc.)
- **JavaScript/TypeScript**: Frontend implementation using modern web technologies

### Dependencies

- **Nuxt 3**: Vue.js framework for building web applications
- **Vue 3**: Progressive JavaScript framework
- **Vue Router**: Client-side routing
- **Web3 Wallet**: Browser extension or wallet provider (MetaMask, WalletConnect, etc.)

### Backend Requirements

This frontend application requires a backend service that implements Web3 authentication, such as:
- **waltid-ktor-authnz**: Ktor authentication framework with Web3 method support
- **waltid-wallet-api**: Wallet API service with Web3 authentication endpoints

## Usage

### Installation

```bash
npm install
```

### Development

Start the development server:

```bash
npm run dev
```

The application will be available at `http://localhost:3000`.

### Production Build

Build the application for production:

```bash
npm run build
```

Preview the production build:

```bash
npm run preview
```

### Integration with Backend

To use Web3 authentication, integrate with a backend service that provides:

1. **Nonce Endpoint**: `GET /auth/web3/nonce` - Returns a challenge token
2. **Verification Endpoint**: `POST /auth/web3/signed` - Verifies the signature and authenticates

Example integration:

```typescript
// Request nonce
const nonceResponse = await fetch('/auth/web3/nonce');
const challenge = await nonceResponse.text();

// Sign with wallet
const accounts = await ethereum.request({ method: 'eth_requestAccounts' });
const address = accounts[0];
const signature = await ethereum.request({
  method: 'personal_sign',
  params: [challenge, address]
});

// Verify signature
const verifyResponse = await fetch('/auth/web3/signed', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    challenge: challenge,
    signed: signature,
    publicKey: address
  })
});

const result = await verifyResponse.json();
// Use result.token for authenticated requests
```

## Architecture

The application follows a simple Nuxt 3 structure:

```
waltid-web-web3login/
├── app.vue          # Main application component
├── nuxt.config.ts  # Nuxt configuration
├── package.json    # Dependencies
└── server/         # Server-side code (if needed)
```

## Related Components

- **waltid-ktor-authnz**: Backend authentication framework with Web3 method support
- **waltid-wallet-api**: Complete wallet API service with Web3 authentication
- **waltid-web-wallet**: Full-featured web wallet with Web3 login support

## Further Development

This is a minimal starter application. To build a complete Web3 login solution:

1. Implement the authentication flow in your Vue components
2. Connect to a backend service with Web3 authentication support
3. Handle wallet connection and signature requests
4. Manage authentication state and tokens
5. Implement protected routes and user sessions

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>
