<div align="center">
 <h1>Identity by walt.id</h1>
 <p>Multi-Platform libraries, powerful APIs and easy-to-use white label apps to build identity and wallet solutions.</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://twitter.com/intent/follow?screen_name=walt_id">
<img src="https://img.shields.io/twitter/follow/walt_id.svg?label=Follow%20@walt_id" alt="Follow @walt_id" />
</a>


</div>

## Getting Started

### Multi-Platform Libraries 
Available for Kotlin/Java and JavaScript environments.

- [Crypto](https://github.com/walt-id/waltid-identity/blob/main/waltid-crypto/README.md) - create and use keys based on different algorithms.
- [DID](https://github.com/walt-id/waltid-identity/blob/main/waltid-did/README.md) - create, register and resolve DIDs on different ecosystems.
- [Openid4vc](https://github.com/walt-id/waltid-identity/blob/main/waltid-openid4vc/README.md) - implementation of the OID4VCI and OIDC4VP protocols.
- [SD-JWT](https://github.com/walt-id/waltid-identity/blob/main/waltid-sdjwt/README.md) - create and verify Selective Disclosure JWTs.

### Services
A set of API's to build issuer, verifier and wallet capabilities into any app.

- **Issuer API** ([Docs](https://docs.oss.walt.id/issuer/api/vc-oid4vc) | [GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-issuer-api)) - enable apps issue credentials (W3C JWTs and SD-JWTs) via OID4VC.
- **Verifier API**  ([Docs](https://docs.oss.walt.id/verifier/api/vc-oid4vc) | [GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-verifier-api)) - enable apps to verify credentials (W3C JWTs and SD-JWTs) via OID4VP/SIOPv2.
- **Wallet API** ([Docs](https://docs.oss.walt.id/wallet/api/getting-started) | [GitHub](https://github.com/walt-id/waltid-identity/blob/main/waltid-web-wallet/README.md)) - extend apps with wallet capabilities to collect, store, manage and share identity credentials and tokens.

### Apps
A set of white-label apps to get started in no time.

- [Web-Wallet](https://github.com/walt-id/waltid-identity/tree/main/waltid-web-wallet/web) - A custodial web-wallet (PWA) solution for credentials and tokens.
- [Portal](https://github.com/walt-id/waltid-identity/tree/main/waltid-web-portal) - An issuer and verifier portal for credentials.


## Building the Project

### Docker container builds:

```shell
docker build -t waltid/issuer -f waltid-issuer-api/Dockerfile .
docker run -p 7000:7000 waltid/issuer --webHost=0.0.0.0 --webPort=7000 --baseUrl=http://localhost:7000
```

```shell
docker build -t waltid/verifier -f waltid-verifier-api/Dockerfile .
docker run -p 7001:7001 waltid/verifier --webHost=0.0.0.0 --webPort=7001 --baseUrl=http://localhost:7001
```


## Architecture
The walt.id identity repo is part of The Community Stack, walt.id's collection of open-source products to build identity and wallet solutions. Learn more [here](https://walt.id/blog/p/community-stack).

![waltid-identity-architecture](https://github.com/walt-id/waltid-identity/assets/48290617/54f2273a-b917-45df-8552-8b41358ed843)

