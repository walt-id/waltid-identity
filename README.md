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

### Multi-Platform Libaries 
Available for Kotlin/Java and JavaScript environments.

- [Crypto](https://github.com/walt-id/waltid-identity/blob/main/waltid-crypto/README.md) - create and use keys based on different algorithms.
- [DID](https://github.com/walt-id/waltid-identity/blob/main/waltid-did/README.md) - create, register and resolve DIDs on different ecosystems.
- [Openid4vc](https://github.com/walt-id/waltid-identity/blob/main/waltid-openid4vc/README.md) - implementation of the OID4VCI and OIDC4VP protocols.
- [SD-JWT](https://github.com/walt-id/waltid-identity/blob/main/waltid-sdjwt/README.md) - create and verify Selective Disclosure JWTs.

### Services
A set of API's to build issuer, verifier and wallet capabilities into any app.

- [Issuer API](https://github.com/walt-id/waltid-identity/tree/main/waltid-issuer-api) - enable apps issue credentials (W3C JWTs, SD-JWTs and JSON-LD) via OID4VC.
- [Verifier API](https://github.com/walt-id/waltid-identity/tree/main/waltid-verifier-api) - enable apps to verify credentials (W3C JWTs, SD-JWTs and JSON-LD) via OID4VP/SIOPv2.
- [Wallet API](https://github.com/walt-id/waltid-identity/blob/main/waltid-web-wallet/README.md) - extend apps with holistic identity wallet capabilities like collecting, storing, managing and sharing of identity credentials.

### Apps
A set of white-label apps to get started in no time.

- Web-Wallet - A custodial web-wallet (PWA) solution for credentials and tokens.
- Portal - An issuer and verifier portal for credentials.


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
