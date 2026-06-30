<div align="center">
<h1>walt.id Applications</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>End-user applications and development tools for digital identity and credentials</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
  
  <h2>Statuses Explained</h2>
  <table>
    <tr>
      <td align="center" width="50%">
        <img src="https://img.shields.io/badge/🟢%20Actively%20Maintained-success?style=for-the-badge&logo=check-circle" alt="Status: Actively Maintained" />
        <br/>
        <em>This project is being actively maintained by the development team at walt.id. Regular updates, bug fixes, and new features are being added.</em>
      </td>
      <td align="center" width="50%">
        <img src="https://img.shields.io/badge/🟡%20Unmaintained-yellow?style=for-the-badge&logo=warning" alt="Status: Unmaintained" />
        <br/>
        <em>This project is not actively maintained. Certain features may be outdated or not working as expected. We encourage users to contribute to the project to help keep it up to date.</em>
      </td>
    </tr> 
    <tr>
      <td align="center" width="50%">
        <img src="https://img.shields.io/badge/🔴%20Deprecated-red?style=for-the-badge&logo=no-entry" alt="Status: Deprecated" />
        <br/>
        <em>This project is deprecated and no longer maintained. It should not be used in new projects. Please use our alternative libraries or migrate to recommended replacements.</em>
      </td>
      <td align="center" width="50%">
        <img src="https://img.shields.io/badge/🟠%20Planned%20Deprecation-orange?style=for-the-badge&logo=clock" alt="Status: Planned Deprecation" />
        <br/>
        <em>This project is still supported by the development team at walt.id, but is planned for deprecation. We encourage users to migrate to using our alternative libraries.</em>
      </td>
    </tr>
  </table>
</div>

## Overview

This directory contains end-user applications and development tools built on top of the walt.id libraries and services. These applications demonstrate real-world usage of walt.id technology and provide tools for developers and users.

## Applications

### Development Tools

### [🟡 waltid-cli](./waltid-cli)
Command-line interface for walt.id operations. Provides a multiplatform CLI tool for credential operations, key management, DID operations, and other walt.id functionality from the command line.

**Use when:** You need a command-line tool for walt.id operations, want to script credential workflows, or need a development tool for testing walt.id functionality.

<br />
<br />

### Web Applications

### [🟢 waltid-web-wallet](./waltid-web-wallet)
Web-based wallet application. Complete wallet implementation built with Vue.js/Nuxt.js, providing a user-friendly interface for managing credentials, handling issuance/presentation flows, and interacting with verifiers.

**Use when:** You need a reference wallet implementation, want to deploy a web-based wallet, or need a wallet for testing and development.

### [🟢 waltid-web-portal](./waltid-web-portal)
Web portal for credential issuance and verification. Next.js-based web application providing interfaces for issuers to create credential offers and verifiers to request presentations.

**Use when:** You need a web interface for credential issuance and verification workflows, or want a reference implementation for building credential portals.

### [🟢 waltid-credentials](./waltid-credentials)
Credential repository for W3C Verifiable Credentials, SD-JWT credentials, and ISO mDoc credentials.

**Use when:** You need a repository of verifiable credential schemas and examples, or want a reference implementation for building a credential repository.

### [🟡 waltid-web-web3login](./waltid-web-web3login)
Web3 login application. Nuxt.js application demonstrating Web3 wallet-based authentication and login flows.

**Use when:** You need a reference implementation for Web3-based authentication or want to understand Web3 login integration patterns.

<br />
<br />

### Mobile Applications

### [🟢 waltid-wallet-demo-compose](./waltid-wallet-demo-compose)
Compose Multiplatform wallet demo for the mobile wallet SDK. Demonstrates credential issuance, presentation, platform-backed keys, and persistence on Android and iOS. The Web/Wasm module is currently a mock UI preview only.

**Use when:** You need the current cross-platform mobile wallet demo, Android or iOS E2E scripts, or a reference for wallet SDK integration in a mobile app.

### [🟢 waltid-wallet-demo-ios](./waltid-wallet-demo-ios)
Native iOS wallet demo app. SwiftUI application backed by Kotlin Multiplatform wallet SDK components, with iOS Keychain / Secure Enclave-backed keys and E2E scripts for public EUDI and local Enterprise flows.

**Use when:** You need a native iOS wallet SDK reference implementation, iOS-specific integration patterns, or iOS wallet E2E coverage.

### [🟢 waltid-android](./waltid-android)
Android sample project for key generation, DID creation, signing, and verification. This app is planned for deprecation; use `waltid-wallet-demo-compose` for the current Android wallet issuance/presentation demo.

**Use when:** You need the legacy Android crypto/sample app while it is still available, or you are migrating existing Android sample usage to the Compose wallet demo.

### [🟢 waltid-wallet-demo-android](./waltid-wallet-demo-android)
Native Android demo app for the mobile Wallet SDK. Demonstrates credential issuance, credential presentation, Android Keystore-backed keys, and SQLDelight-backed wallet persistence.

**Use when:** You need to validate mobile wallet SDK flows on Android or run Android mobile integration tests.

### [🟢 waltid-wallet-demo-ios](./waltid-wallet-demo-ios)
Native iOS demo app for the mobile Wallet SDK. Demonstrates credential issuance, credential presentation, iOS Keychain / Secure Enclave-backed keys, and SQLDelight-backed wallet persistence.

**Use when:** You need to validate mobile wallet SDK flows on iOS or run iOS mobile integration tests.

### [🟢 waltid-digital-credentials](./waltid-digital-credentials)
Standalone web test app for validating the Digital Credentials API flow directly against verifier endpoints. Provides a user-friendly interface for testing and debugging DC API verification flows.

**Use when:** You want to test the Digital Credentials API verification flow against walt.id verifier endpoints.

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../assets/walt-banner.png" alt="walt.id banner" />
</div>
