<div align="center">
<img src="assets/walt-banner.png" alt="walt.id banner" />

  <p>Multi-Platform libraries, powerful APIs and easy-to-use white label apps to build identity and wallet solutions <span>by </span><a href="https://walt.id">walt.id</a></p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
</div>

## Getting Started

Welcome to the walt.id identity repository! We are a team of developers who are passionate about
building open tooling for digital identity and wallet solutions. We have a wealth of resources,
applications and libraries which may be confusing at first glance, but hopefully this will help you
get started based on what you want to do.

The best place to find more information about concepts and usage of the Walt products is
our [docs site](https://docs.walt.id).

Once you've understood the basics and, you can start using some of our repositories to build your
own solutions, depending on if you're trying to....

### Test out the Walt product line

All of our apis are hosted at demo.walt.id. Depending on the service you are interested in, you can visit the following links:
- **Issuer API** - ([Portal (coming soon)]| [Swagger](https://issuer2.demo.walt.id/swagger)| [Docs](https://docs.walt.id/community-stack/issuer2/getting-started)  | [GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-services/waltid-issuer-api2)) - enable apps to issue credentials (W3C JWTs, SD-JWTs, mdoc) via OID4VCI 1.0.
- **Verifier API** - ([Portal (coming soon)] | [Swagger](https://verifier2.demo.walt.id/swagger) | [Docs](https://docs.walt.id/community-stack/verifier2/getting-started) | [GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-services/waltid-verifier-api2)) - enable apps to verify credentials (W3C JWTs, SD-JWTs, mdoc) via OID4VP/SIOPv2 1.0.
- **Wallet API** - ([Portal (coming soon)] | [Swagger](https://wallet2.demo.walt.id/swagger) | [Docs](https://docs.walt.id/community-stack/wallet2/getting-started) | [GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-services/waltid-wallet-api2)) - extend apps with wallet capabilities to collect, store, manage and share identity credentials and tokens.
- **Wallet SDK** - ([Docs (coming soon)] | [GitHub (coming soon)]) - a complete wallet library supporting both OpenID4VCI 1.0 and OpenID4VP 1.0.
    - **Android Wallet** - ([apk (coming soon)] | [source code (coming soon)])
    - **iOS Wallet** - ([testflight (coming soon)] | [source code (coming soon)])

We are still in the process of building new open source portals to allow you to quickly test out the products!

Use
the [walt.id identity package](https://github.com/walt-id/waltid-identity/tree/main/docker-compose)
to run all APIs and Apps with docker:

**Clone walt.id identity**

```bash
git clone https://github.com/walt-id/waltid-identity.git && cd waltid-identity
```

**Launch the services**

```bash
cd docker-compose && docker compose up
```

Learn more about the docker settings & exposed
ports [here](https://github.com/walt-id/waltid-identity/tree/main/docker-compose).

### Build digital credential tooling and applications

If you need even more customisability and control, you can build your own tooling and applications
using the same libraries that we use for the APIs and applications above. We try to provide
multiplatform libraries so you can build application running on JVM, JavaScript and iOS platforms.
Some popular libraries you may want to look at are:

- **Crypto** ([GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/crypto/waltid-crypto)) -
  create and use keys based on different algorithms and KMS backends (in-memory, AWS, Hashicorp TSE,
  OCI)
- **DID** ([GitHub](https://github.com/walt-id/waltid-identity/blob/main/waltid-libraries/waltid-did/README.md)) -
  create, register, and resolve DIDs on different ecosystems.
- **W3C Credentials** ([GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/credentials/waltid-w3c-credentials)) -
  issue and verify W3C credentials as JWTs and SD-JWTs.
- **mdoc Credentials** ([GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/credentials/waltid-mdoc-credentials2)) -
  issue and verify mdoc credentials (mDL ISO/IEC 18013-5).
- **SD-JWT** ([GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/sdjwt/waltid-sdjwt)) -
  create and verify Selective Disclosure JWTs.
- **OpenID4VCI** ([GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/protocols/waltid-openid4vci)) -
  implementation of the OID4VCI 1.0 protocol.
  protocols. Results
  from [OpenID Foundation's Conformance Suite (coming soon)]
- **OpenID4VP** ([GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/protocols/waltid-openid4vp)) -
  implementation of the OpenID4VP 1.0 protocol. Results
  from [OpenID Foundation's Conformance Suite](https://conformance.waltid.cloud/logs.html)
- **Core Wallet** ([GitHub](https://github.com/walt-id/waltid-identity/tree/main/waltid-libraries/protocols/waltid-openid4vc-wallet)) -
  implementation of the Core Wallet library supporting both OpenID4VCI 1.0 and OpenID4VP 1.0.

## Architecture

TO DO

## Join the community

* Connect and get the latest
  updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report
  bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under
the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)
