<div align="center">
 <h1>Web Wallet (Frontend)</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Custodian white-label web-wallet solution for Verifiable Credentials and NFTs<p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://twitter.com/intent/follow?screen_name=walt_id">
<img src="https://img.shields.io/twitter/follow/walt_id.svg?label=Follow%20@walt_id" alt="Follow @walt_id" />
</a>


</div>

## Getting Started

- [Intro Video](https://youtu.be/HW9CNFmRFlI) - Learn about features and see a demo.


### Running the project using Docker

From the root-folder you can run the wallet-api including the necessary configuration as well as other relevant services and apps like the wallet frontend by the following command:
```bash
cd docker-compose && docker compose up
```

- Visit the demo web wallet hosted under [localhost:7101](http://localhost:7101).
- Visit the dev web wallet hosted under [localhost:7104](http://localhost:7104).
- Visit the wallet-api hosted under /swagger endpoint.

Update the containers by running the following commands from the root folder: 
```bash
docker build -f waltid-applications/waltid-web-wallet/apps/waltid-demo-wallet/Dockerfile -t waltid/waltid-demo-wallet .
docker build -f waltid-applications/waltid-web-wallet/apps/waltid-dev-wallet/Dockerfile -t waltid/waltid-dev-wallet .
```
Note that this is project only the frontend of the web-wallet. The corresponding backend is in the /waltid-wallet-api folder.

### Running the project for development

```bash
npm install
cd apps/waltid-demo-wallet && npm run dev
```

## What is the Web-Wallet?

A **white-label solution** built on top of our core libs, offering you everything to build identity POCs leveraging Verifiable Credentials and NFTs/Tokens across ecosystems.

### Features

Features are provided by our libraries SSI-Kit and NFT-Kit to enable SSI and NFT functionality

### SSI

- **Manage VCs** - Receive, Store and Present Verifiable Credentials with different formats (e.g. W3C) and proof-types (e.g. JWT, SD-JWT). Full overview of formats and proof-types [here](https://walt-id.notion.site/Features-by-Product-aab646e46a744a7d84a6b8fd6b7066ac?pvs=4).
- **Manage DIDs** - Create and delete DIDs and onboard to different ecosystems (e.g. did:key, did:jwt, did:ebsi, did:cheqd, did:web and more) Full list [here](https://walt-id.notion.site/Features-by-Product-aab646e46a744a7d84a6b8fd6b7066ac?pvs=4).
- **Manage Keys** - Create, import and export keys with different algorithms (e.g. Ed25519). Full list [here](https://walt-id.notion.site/Features-by-Product-aab646e46a744a7d84a6b8fd6b7066ac?pvs=4).
  
- **Credential Exchange** - Based on OIDC standards (OIDC4VC & OIDC4VP) using synchronous flows with same device and cross device support as well as asynchronous flows leveraging the browser notification system.

### NFTs

- **View NFTs** - Display NFTs from different ecosystems (e.g. Ethereum, Near, Flow and more) in one coherent interface.


## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [Twitter](https://mobile.twitter.com/walt_id)
* Get help, request features and report bugs: [GitHub Issues ](https://github.com/walt-id/waltid-identity/issues)

## License

**Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-ssikit/blob/master/LICENSE).**
