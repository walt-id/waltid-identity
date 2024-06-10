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

- **Crypto** ([Docs](https://docs.oss.walt.id/issuer/sdks/manage-keys/overview) | [GitHub](https://github.com/walt-id/waltid-identity/blob/main/waltid-crypto/README.md)) - create and use keys based on different algorithms.
- **DID** ([Docs](https://docs.oss.walt.id/issuer/sdks/manage-dids/overview) | [GitHub](https://github.com/walt-id/waltid-identity/blob/main/waltid-did/README.md)) - create, register, and resolve DIDs on different ecosystems.
- **Verifiable Credentials** ([Docs](https://docs.oss.walt.id/issuer/sdks/manage-credentials/overview) | [GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-verifiable-credentials)) - issue and verify W3C credentials as JWTs and SD-JWTs.
- **mdoc Credentials** ([GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-mdoc-credentials)) - issue and verify mdoc credentials (mDL ISO/IEC 18013-5).
- **OpenID4VC** ([GitHub](https://github.com/walt-id/waltid-identity/blob/main/waltid-openid4vc/README.md)) - implementation of the OID4VCI and OIDC4VP protocols.
- **SD-JWT** ([GitHub](https://github.com/walt-id/waltid-identity/blob/main/waltid-sdjwt/README.md)) - create and verify Selective Disclosure JWTs.

### Services
A set of APIs to build issuer, verifier, and wallet capabilities into any app.

- **Issuer API** ([Docs](https://docs.oss.walt.id/issuer/api/getting-started) | [GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-issuer-api)) - enable apps to issue credentials (W3C JWTs and SD-JWTs) via OID4VC.
- **Verifier API**  ([Docs](https://docs.oss.walt.id/verifier/api/getting-started) | [GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-verifier-api)) - enable apps to verify credentials (W3C JWTs and SD-JWTs) via OID4VP/SIOPv2.
- **Wallet API** ([Docs](https://docs.oss.walt.id/wallet/api/getting-started) | [GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-wallet-api)) - extend apps with wallet capabilities to collect, store, manage and share identity credentials and tokens.

### Apps
A set of white-label apps to get started in no time.

- [Web-Wallet](https://github.com/walt-id/waltid-identity/tree/main/waltid-web-wallet) - A custodial web-wallet (PWA) solution for credentials and tokens.
- [Portal](https://github.com/walt-id/waltid-identity/tree/main/waltid-web-portal) - An issuer and verifier portal for credentials, which are managed [here](https://github.com/walt-id/waltid-credentials).

## Use Services And Apps

Add the following lines in your hosts file:

 - On windows `(C:\Windows\System32\drivers\etc\hosts)`

 - On linux `(/etc/hosts)`

```bash
127.0.0.1 issuer-api
127.0.0.1 verifier-api
````



Use the [walt.id identity package](https://github.com/walt-id/waltid-identity/tree/main/docker-compose) to run all APIs and Apps with docker: 

```bash
cd docker-compose && docker-compose up
```

Learn more about the exposed ports [here](https://github.com/walt-id/waltid-identity/tree/main/docker-compose).

## Architecture
The walt.id identity repo is part of The Community Stack, walt.id's collection of open-source products to build identity and wallet solutions. Learn more [here](https://walt.id/blog/p/community-stack).

![waltid-identity-architecture](https://github.com/walt-id/waltid-identity/assets/48290617/b7ca5662-53cc-488c-bfe5-a58c89cd2ec0)


## Join the community

* Connect and get the latest updates: <a href="https://discord.gg/AW8AgqJthZ">Discord</a> | <a href="https://walt.id/newsletter">Newsletter</a> | <a href="https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA">YouTube</a> | <a href="https://mobile.twitter.com/walt_id" target="_blank">Twitter</a>
* Get help, request features and report bugs: <a href="https://github.com/walt-id/.github/discussions" target="_blank">GitHub Discussions</a>

## License

**Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-ssikit/blob/master/LICENSE).**

