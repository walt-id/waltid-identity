<div align="center">
<h1>walt.id OpenID4VC Wallet Mobile</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Mobile facade for Android and iOS wallet SDK integrations.</p>

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

## Overview

Mobile facade for the walt.id wallet SDK. This module exposes the Android/iOS-facing wallet API used by the native demo apps while delegating protocol behavior to [waltid-openid4vc-wallet](../waltid-openid4vc-wallet/README.md) and persistence to [waltid-openid4vc-wallet-persistence-mobile](../waltid-openid4vc-wallet-persistence-mobile/README.md).

Native iOS apps should consume the Swift [WalletSDK](../waltid-wallet-sdk-ios/README.md) package, which wraps this KMP module behind Swift-owned types, `async`/`await`, and DocC documentation.

For local setup and platform build flags, see the [Mobile Wallet Development Guide](../../../docs/mobile-wallet-development.md).

## Package map

Open [MobileWalletFactory](src/commonMain/kotlin/id/walt/wallet2/mobile/MobileWalletFactory.kt)
with a [MobileWalletConfig](src/commonMain/kotlin/id/walt/wallet2/mobile/MobileWalletConfig.kt).
The returned [MobileWallet](src/commonMain/kotlin/id/walt/wallet2/mobile/MobileWallet.kt) owns the
session and its lifecycle. Import feature types from these packages under `id.walt.wallet2.mobile`.

| Package | Public responsibilities |
| --- | --- |
| [identity](src/commonMain/kotlin/id/walt/wallet2/mobile/identity) | Signing identities, key authorization, custody and recovery |
| [credentials](src/commonMain/kotlin/id/walt/wallet2/mobile/credentials) | Stored credentials, status reads and local persistence settings |
| [issuance](src/commonMain/kotlin/id/walt/wallet2/mobile/issuance) | Offers, holder bindings, accepted credentials and attestation settings |
| [presentation](src/commonMain/kotlin/id/walt/wallet2/mobile/presentation) | Verifier requests, disclosure choices, consent and response delivery |
| [proximity](src/commonMain/kotlin/id/walt/wallet2/mobile/proximity) | Nearby transport, reader trust, approval and session state |
| [digitalcredentials](src/commonMain/kotlin/id/walt/wallet2/mobile/digitalcredentials) | OS registration, digital credential requests and Annex C responses |

Configuration captures device language preferences by default on Android and iOS. Pass
an explicit empty list to skip preferred-language lookup. Key protection, issuer integrations,
verifier trust and OS credential registration each have their own settings. Opening a default
wallet requires no configuration arguments.

## Capabilities

- Create, select, back up and restore an explicitly bound signing key and DID.
- Start and continue OpenID4VCI issuance sessions.
- List credentials stored in mobile persistence.
- Present credentials using OpenID4VP.
- Present mdocs in person through a stateful ISO/IEC 18013-5 proximity session.
- Support mobile issuance flows using OAuth 2.0 client attestation.

## Signing identity and recovery

Use `wallet.signingIdentity.initialize()` for the default P-256 / `did:jwk` identity,
or request SDK-issued creation and restoration options for explicit configuration.
Recovery providers are optional dependencies and registrations. The base SDK enables no backup.

See the [identity lifecycle guide](docs/identity-recovery.md) for the platform matrix, configuration,
recovery limitations and standards boundaries, and the [versioned recovery format](docs/identity-recovery-format.md).
The [recovery qualification guide](docs/recovery-testing.md) describes the isolated test hosts, resumable phases and evidence boundaries.

## Key-use authorization

New wallet keys default to `BiometricCurrentSet`; callers that need unprotected
signing must explicitly select `KeyUseAuthorizationPolicy.None`. The protected
policy is P-256 only, requires a current resumed Android `FragmentActivity` for
each signing prompt, rejects device-credential fallback, and invalidates the key
when the biometric enrollment set changes. iOS biometric signing requires an `NSFaceIDUsageDescription` host-app entry. Hardware signing
requires a physical Secure Enclave device; recoverable identities use ordinary Keychain signing.

The policy is chosen only while creating a new key. Restored keys retain their
persisted policy; changing the default never weakens or recreates an existing key.

`BiometricTimedReuse(timeoutSeconds)` is available for a fixed 1–30 second,
non-sliding reuse interval. It also requires P-256, strong biometrics, and no
device-credential fallback, but intentionally permits new biometric enrollment
without binding the key to the current set. Resetting all biometrics can still make ordinary iOS
Keychain keys unavailable; see the [lifecycle limitations](docs/identity-recovery.md#platform-capabilities). Android reports `PlatformKeyStore` with
`IndependentReadback`: native KeyStore metadata can be read back and compared
with the requested interval after creation or restoration. iOS reports
`ProviderProcess` with `ProviderConfigurationOnly`: the Apple adapter retains a per-key
LocalAuthentication context for the configured interval; native Keychain metadata cannot independently
verify that interval. Android reuse may cover other eligible keys. Timed reuse is recent platform or
provider authentication, not consent for issuance, presentation, or another wallet action.

## HTTP response caching

The default iOS transport disables Foundation `URLCache` for SDK HTTP sessions,
including immediate credential responses and deferred issuance polling. This
prevents Foundation from retaining credential bodies outside wallet persistence,
even when an external issuer omits `Cache-Control: no-store`. CMP iOS and the Swift
WalletSDK use this transport by default.

Caller-supplied HTTP clients must apply the same policy. A custom Ktor Darwin client
should use `engine { configureSession { URLCache = null } }`; a custom `URLSession`
should set `configuration.urlCache = nil` before creating the session. A request
policy that ignores cached responses does not prevent storing new response bodies.

## Credential status

Read the wallet's local status snapshot, observe updates, and explicitly refresh when the user or app requests a check. The presentation review includes the advisories for its selected credentials. See the [credential status guide](https://docs.walt.id/community-stack/mobile-wallet/security#credential-status).

## Receiving and presenting credentials

Open a wallet with `factory.create()`. Initialize the signing identity, preview an offer, then accept its grant. Supply the registered client when starting browser authorization. An optional issuer receipt profile belongs in the issuance configuration.

Preview a presentation URL, choose credentials and disclosures, and show the review returned by the wallet. After confirmation, submit that review. A prepared redirect or form requires host delivery.

The [compiled Kotlin journey](src/commonTest/kotlin/examples/WalletJourney.kt) covers setup, both issuance grants, reviewed sharing and closing the wallet. The [receiving](https://docs.walt.id/community-stack/mobile-wallet/receive-credentials) and [presenting](https://docs.walt.id/community-stack/mobile-wallet/present-credentials) guides explain each step.

## In-person proximity presentation

The [proximity guide](docs/proximity.md) covers platform transports, reader trust, approval, cancellation and prepared sharing.

## Persistence and encryption

The [persistence guide](docs/persistence.md) covers encrypted storage, database key ownership, custom stores and deletion.

## Android credential registry

Android registers claim paths and scalar matching values with Credential Manager.
Compound values, JSON null, data URIs and recognized base64 image payloads retain
their paths without a matching value. They can be requested by field presence,
but cannot satisfy an exact-value constraint in the platform matcher. Ordinary
scalar values retain their types and full matching values; picker display text
is limited to 128 characters. Full claim payloads remain in wallet storage and
presentation processing.

This follows AndroidX's nullable [`MdocField.fieldValue`](https://developer.android.com/reference/kotlin/androidx/credentials/registry/digitalcredentials/mdoc/MdocField)
contract for fields such as photos, and also applies to SD-JWT registry claims.
Check `digitalCredentialRegistration` or the result of
`refreshDigitalCredentialRegistration()` to detect and retry platform indexing
failures independently of wallet loading. Refreshes on one wallet serialize the credential snapshot
and its publication. Cancellation propagates without publishing a completed result. Android also
orders platform writes across process-local adapters: a submitted write retains its place until the
provider callback finishes, even if the caller cancels, because Play services may ignore cancellation.
A provider that never completes a callback blocks subsequent registry writes in that process; local
wallet reads remain independent.

## Demo apps

- [Compose Wallet Demo](../../../waltid-applications/waltid-wallet-demo-compose/README.md)
- [iOS Wallet Demo](../../../waltid-applications/waltid-wallet-demo-ios/README.md)

## API documentation

Generate the SDK facade API reference with Dokka:

```bash
./gradlew :waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:dokkaGeneratePublicationHtml -PenableAndroidBuild=true -PenableIosBuild=true
```

The generated HTML is written to `build/dokka/html`.

The native Swift iOS facade has a separate DocC catalog in [WalletSDK](../waltid-wallet-sdk-ios/README.md).

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
