<div align="center">
 <h1>Wallet Service</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Holistic identity wallet capabilities like
collecting, storing, managing and sharing of identity credentials.<p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://twitter.com/intent/follow?screen_name=walt_id">
<img src="https://img.shields.io/twitter/follow/walt_id.svg?label=Follow%20@walt_id" alt="Follow @walt_id" />
</a>


</div>

## What it provides

- account management (email, web3 wallets)
- verifiable credential management (W3C) - store, delete
- decentralized identifiers (DIDs) management - create / register, store, delete
- cryptographic keys management - create, import / export, delete
- verifiable credential exchange based on OID4VC standards (OID4VCI & OID4VP):
  - synchronous flow (same / cross device)
  - asynchronous flow
- NFT visualization

Wallet service relies on the following walt.id libraries:

- [waltid-openid4vc library](https://github.com/walt-id/waltid-identity/tree/main/waltid-openid4vc)
  for OID4VC interactions
- [waltid-did library](https://github.com/walt-id/waltid-identity/tree/main/waltid-did)
  for DID related operations
- [waltid-crypto library](https://github.com/walt-id/waltid-identity/tree/main/waltid-crypto)
  for key related operations

## How to use it

### Running from source

1. run the `id.walt.ApplicationKt` file
2. the wallet backend is available at: http://localhost:4545

### Running with docker

```shell
docker run \
-v ${pwd}/waltid-wallet/config:/waltid-web-wallet/bin/waltid-web-wallet \
-p 4545:4545 waltid/wallet-backend:latest
```