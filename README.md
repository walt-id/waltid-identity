<div align="center">
<img src="assets/walt-banner.png" alt="walt.id banner" />
 
  <p>Multi-Platform libraries, powerful APIs and easy-to-use white label apps to build identity and wallet solutions <span>by </span><a href="https://walt.id">walt.id</a></p>

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

## Getting Started

### Multi-Platform Libraries

Available for Kotlin/Java and JavaScript environments.

- **Crypto** ([GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/crypto/waltid-crypto)) -
  create and use keys based on different algorithms and KMS backends (in-memory, AWS, Hashicorp TSE, OCI)
- **DID** ([GitHub](https://github.com/walt-id/waltid-identity/blob/main/waltid-libraries/waltid-did/README.md)) -
  create, register, and resolve DIDs on different ecosystems.
- **W3C Credentials** ([GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/credentials/waltid-w3c-credentials)) -
  issue and verify W3C credentials as JWTs and SD-JWTs.
- **mdoc Credentials** ([GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/credentials/waltid-mdoc-credentials)) -
  issue and verify mdoc credentials (mDL ISO/IEC 18013-5).
- **SD-JWT** ([GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/sdjwt/waltid-sdjwt)) -
  create and verify Selective Disclosure JWTs.
- **OpenID4VC** ([GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/protocols/waltid-openid4vc)) -
  implementation of the OID4VCI (draft 11 and draft 13) and OIDC4VP (draft 14 and draft 20) protocols.


### REST Services

A set of APIs to build issuer, verifier, and wallet capabilities into any app.

- **Issuer API** ([Docs](https://docs.walt.id/community-stack/issuer/api/getting-started) | [GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-services/waltid-issuer-api)) -
  enable apps to issue credentials (W3C JWTs and SD-JWTs) via OID4VC.
- **Verifier API**  ([Docs](https://docs.walt.id/community-stack/verifier/api/getting-started) | [GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-services/waltid-verifier-api)) -
  enable apps to verify credentials (W3C JWTs and SD-JWTs) via OID4VP/SIOPv2.
- **Wallet API** ([Docs](https://docs.walt.id/community-stack/wallet/api/getting-started) | [GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-services/waltid-wallet-api)) -
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

![waltid-identity-architecture](https://github.com/user-attachments/assets/56a69598-c9f0-4f4a-a071-05fb98d247ba)

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues ](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)


## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)