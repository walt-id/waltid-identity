<div align="center">
 <h1>Credential Issuance & Verification Portal</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Sample application that showcases the issuance and verification of W3C Verifiable Credentials</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
</div>

## Overview

This web portal integrates four core walt.id services to provide a complete credential lifecycle:

- **Credentials API** (`NEXT_PUBLIC_VC_REPO`): Serves credential schemas and templates
- **Issuer API** (`NEXT_PUBLIC_ISSUER`): Issues verifiable credentials to wallets
- **Verifier API** (`NEXT_PUBLIC_VERIFIER`): Requests and verifies credentials from wallets
- **Wallet** (`NEXT_PUBLIC_WALLET`): End-user wallet for receiving and presenting credentials

The portal acts as a frontend that orchestrates these services to demonstrate credential issuance and verification flows.

## Getting Started

First, run the development server:

```bash
pnpm install
pnpm dev
```

***Note!*** The web portal requires the environment variables to run.
Locally, they can be set up using a `.env` or `.env.local` file:

```text
NEXT_PUBLIC_VC_REPO=https://credentials.walt.id
NEXT_PUBLIC_ISSUER=https://issuer.portal.walt.id
NEXT_PUBLIC_VERIFIER=https://verifier.portal.walt.id
NEXT_PUBLIC_WALLET=https://wallet.walt.id
``` 

Build for production

```bash
pnpm install
pnpm build
```

Using Docker:

```bash
docker build -t waltid/portal -f waltid-web-portal/Dockerfile .
docker run -p 7102:7102 -i -t waltid/portal
```

## Adding New Credentials

To add support for new credential types:

1. **Add credential schema** to the Credentials API repository
2. **Update issuer metadata** to include the new credential type in supported credentials
3. The portal will automatically discover and display new credentials from the configured endpoints

Both the credential repository and issuer metadata must be updated for credentials to appear in the issuance flow.

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues ](https://github.com/walt-id/waltid-identity/issues)


## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)
