<div align="center">
 <h1>Identity by walt.id</h1>
 <p>Multi-Platform libraries, powerful APIs and easy-to-use white label apps to build identity and wallet solutions.</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://twitter.com/intent/follow?screen_name=walt_id">
<img src="https://img.shields.io/twitter/follow/walt_id.svg?label=Follow%20@walt_id" alt="Follow @walt_id" />
<img alt="GitHub commits since latest release" src="https://img.shields.io/github/commits-since/walt-id/waltid-identity/latest"></a>
</div>

## Getting Started

### Multi-Platform Libraries

Available for Kotlin/Java and JavaScript environments.

- **Crypto** ([Docs](https://docs.oss.walt.id/issuer/sdks/manage-keys/overview) | [GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/crypto/waltid-crypto)) -
  create and use keys based on different algorithms and KMS backends (in-memory, AWS, Hashicorp TSE, OCI)
- **DID** ([Docs](https://docs.oss.walt.id/issuer/sdks/manage-dids/overview) | [GitHub](https://github.com/walt-id/waltid-identity/blob/main/waltid-libraries/waltid-did/README.md)) -
  create, register, and resolve DIDs on different ecosystems.
- **W3C Credentials** ([Docs](https://docs.oss.walt.id/issuer/sdks/manage-credentials/overview) | [GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/credentials/waltid-w3c-credentials)) -
  issue and verify W3C credentials as JWTs and SD-JWTs.
- **mdoc Credentials** ([GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/credentials/waltid-mdoc-credentials)) -
  issue and verify mdoc credentials (mDL ISO/IEC 18013-5).
- **OpenID4VC** ([GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/protocols/waltid-openid4vc)) -
  implementation of the OID4VCI and OIDC4VP protocols.
- **SD-JWT** ([GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/sdjwt/waltid-sdjwt)) -
  create and verify Selective Disclosure JWTs.
- **Ktor-Authnz** ([GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/auth/waltid-ktor-authnz)) - Add various authentication methods (OIDC, Email/Password, ...) to Ktor projects.
- **Permissions** ([GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/auth/waltid-permissions)) - Enable fine-grained authorisation patterns in applications with waltid-permissions.

### REST Services

A set of APIs to build issuer, verifier, and wallet capabilities into any app.

- **Issuer API** ([Docs](https://docs.walt.id/issuer/api/getting-started) | [GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-services/waltid-issuer-api)) -
  enable apps to issue credentials (W3C JWTs and SD-JWTs) via OID4VC.
- **Verifier API**  ([Docs](https://docs.walt.id/verifier/api/getting-started) | [GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-services/waltid-verifier-api)) -
  enable apps to verify credentials (W3C JWTs and SD-JWTs) via OID4VP/SIOPv2.
- **Wallet API** ([Docs](https://docs.oss.walt.id/wallet/api/getting-started) | [GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-services/waltid-wallet-api)) -
  extend apps with wallet capabilities to collect, store, manage and share identity credentials and tokens.

### Apps

A set of white-label apps to get started in no time.

- **Web-Wallets** ([Demo](https://wallet.walt.id/login) | [Docs](https://docs.walt.id/community-stack/wallet/apps/web-wallet/overview) | [GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-applications/waltid-web-wallet)) - Custodial web-wallet (PWA) solutions for credentials and tokens.
- **Portal** ([Demo](https://portal.walt.id/) | [Docs](https://docs.walt.id/community-stack/issuer/apps/portal/overview) | [GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-applications/waltid-web-portal)) - An issuer and verifier portal for credentials, which are managed [here](https://github.com/walt-id/waltid-credentials).


## Use REST Services And Apps

Use the [walt.id identity package](https://github.com/walt-id/waltid-identity/tree/main/docker-compose) to run all APIs and Apps with docker:

**Clone walt.id identity**

```bash
git clone https://github.com/walt-id/waltid-identity.git && cd waltid-identity
```

**Launch the services**

```bash
cd docker-compose && docker compose up
```

Learn more about the docker settings & exposed ports [here](https://github.com/walt-id/waltid-identity/tree/main/docker-compose).

## Use the Command Line Tool

Use the [walt.id CLI](https://github.com/walt-id/waltid-identity/tree/main/waltid-applications/waltid-cli) to run the
core functions from
the command line. Make sure you have your Java Runtime set up.

**Clone walt.id identity**

```bash
git clone https://github.com/walt-id/waltid-identity.git && cd waltid-identity
```

**Access CLI**

```bash
cd waltid-applications/waltid-cli && ./waltid-cli.sh
```

## Architecture

The walt.id identity repo is part of The Community Stack, walt.id's collection of open-source products to build identity and wallet
solutions. Learn more [here](https://walt.id/blog/p/community-stack).

![waltid-identity-architecture](https://github.com/user-attachments/assets/98c020fe-dc37-46fd-9886-613ee8fc8760)

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [Twitter](https://mobile.twitter.com/walt_id)
* Get help, request features and report bugs: [GitHub Issues ](https://github.com/walt-id/waltid-identity/issues)

## License

**Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-ssikit/blob/master/LICENSE).**

