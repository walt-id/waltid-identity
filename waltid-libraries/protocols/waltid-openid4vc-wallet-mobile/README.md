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

## Capabilities

- Bootstrap a mobile wallet with platform-backed keys and DID material.
- Start and continue OpenID4VCI issuance sessions.
- List credentials stored in mobile persistence.
- Present credentials using OpenID4VP.
- Present mdocs in person through a stateful ISO/IEC 18013-5 proximity session.
- Support mobile issuance flows using OAuth 2.0 client attestation.

## Key-use authorization

New wallet keys default to `BiometricCurrentSet`; callers that need unprotected
signing must explicitly select `KeyUseAuthorizationPolicy.None`. The protected
policy is P-256 only, requires a current resumed Android `FragmentActivity` for
each signing prompt, rejects device-credential fallback, and invalidates the key
when the biometric enrollment set changes. iOS protected keys require a physical
Secure Enclave device and an `NSFaceIDUsageDescription` host-app entry.

The policy is chosen only while creating a new key. Restored keys retain their
persisted policy; changing the default never weakens or recreates an existing key.

`BiometricTimedReuse(timeoutSeconds)` is available for a fixed 1–30 second,
non-sliding reuse interval. It also requires P-256, strong biometrics, and no
device-credential fallback, but intentionally permits new biometric enrollment
without invalidating the key. Android reports `PlatformKeyStore` with
`IndependentReadback`: native KeyStore metadata can be read back and compared
with the requested interval after creation or restoration. iOS reports
`ProviderProcess` with `ProviderConfigurationOnly`: Signum receives the
requested interval, but its pinned public API cannot independently expose the
effective positive timeout after restoration. Timed reuse is recent
platform or provider authentication, not issuance, presentation, or other
wallet-action consent, and is not guaranteed to be key-local.

## Receiving credentials

Start an issuance session to resolve the offer and retain the exact reviewed
session state while the application collects a separately delivered transaction
code when the issuer requires one:

```kotlin
val session = wallet.startIssuance(
    MobileWalletIssuanceRequest(offer = MobileWalletCredentialOffer.Uri(offerUrl))
)
// Or, for Digital Credentials API CREATE_CREDENTIAL handoffs:
// val session = wallet.startIssuance(
//     MobileWalletIssuanceRequest(offer = MobileWalletCredentialOffer.InlineJson(offerJson))
// )
val transactionCode = session.offer.transactionCode?.let { requirement ->
    collectTransactionCode(
        inputMode = requirement.inputMode ?: "numeric",
        expectedLength = requirement.length,
        description = requirement.descriptionText,
    )
}
val outcome = wallet.continuePreAuthorizedIssuance(session.id, transactionCode)
val credentialIds = (outcome as? WalletIssuanceOutcome.Stored)?.credentialIds
    ?: error("Issuance did not store credentials: $outcome")
```

`MobileWalletCredentialOffer.Uri` is used for deep-link / QR offers.
`MobileWalletCredentialOffer.InlineJson` is the Credential Offer object from an
OpenID4VCI Digital Credentials create request (`openid4vci-v1`, plus historical
aliases). On Android, use
`AndroidDigitalCredentialCreateProvider` to extract that request from Credential
Manager and `AndroidDigitalCredentialRegistry.registerCreationOptions` to
advertise issuance capability separately from presentation registry replacement.

The session contains typed issuer, credential-configuration, and transaction-code
metadata for review UI. For an authorization-code offer, call
`beginAuthorizationIssuance(session.id)` after the user accepts the reviewed
offer, open the returned browser URL, and then continue with
`continueAuthorizationIssuance`. Set `MobileWalletConfig.preferredLocales` to
the app's ordered BCP 47 language preferences; platform demos pass their
platform locale preferences.

## Presenting credentials

Preview a presentation request before submission. The request information includes
typed verifier metadata and the response-encryption state selected by the protocol
implementation:

```kotlin
val preview = wallet.previewPresentation(requestUrl)
preview.request.verifierMetadata?.display?.name?.let(::showVerifierName)
when (val encryption = preview.request.responseEncryption) {
    MobileWalletResponseEncryption.NotRequired -> showPlainResponseNotice()
    is MobileWalletResponseEncryption.Required -> showEncryptedResponseNotice(
        algorithm = encryption.keyManagementAlgorithm,
        contentEncryption = encryption.contentEncryptionAlgorithm,
        verifierKeyId = encryption.verifierKeyId,
        verifierKeyThumbprint = encryption.verifierKeyThumbprint,
    )
}
```

Response-encryption metadata describes protection of the authorization response. It
does not establish verifier trust and does not expose verifier key material.

## In-person proximity presentation

Proximity presentation is a distinct session API rather than an OpenID4VP URL
flow. Query capabilities without creating ephemeral keys or radio resources,
then create one single-use session and render its authoritative state:

```kotlin
val configuration = MobileWalletProximityConfiguration()
val capabilities = wallet.proximityPresentationCapabilities(configuration)
val session = wallet.startProximityPresentation(configuration)

session.state.collect { state ->
    when (state) {
        is MobileWalletProximityState.CheckingPrerequisites -> {
            showUnavailableMethods(state.capabilities)
        }
        is MobileWalletProximityState.EngagementReady -> {
            val qr = state.engagements.filterIsInstance<MobileWalletProximityEngagement.Qr>().single()
            showEngagementQr(qr.payload)
        }
        is MobileWalletProximityState.ReviewRequired -> showProximityReview(state.review)
        is MobileWalletProximityState.Completed -> showCompletion(state.exchanges)
        is MobileWalletProximityState.Failed -> showProximityError(state.error)
        else -> showProximityProgress(state)
    }
}
```

The default configuration selects QR engagement and BLE retrieval. NFC and
Wi-Fi Aware are represented in the capability contract but currently report
precise unavailable results until their platform adapters are installed. A
selected unavailable method prevents session preparation; it is never silently
dropped.

Device signature is the default holder-authentication policy. Applications may
require MAC or choose an explicit pre-review preference with
`deviceAuthenticationPolicy`; the selected method is shown on each credential
option, bound into the immutable review, and never changed after consent. The
pinned EUDI profile currently requires device signature.

Host applications perform permission or settings effects named by
`capabilities.remediationActions`, report the privacy-safe outcome with
`MobileWalletProximityAction.ReportRemediation`, and let the SDK re-check the
platform. Review approval uses only the credential and element choices in the
current immutable review. The SDK revalidates credential, holder key, reader
trust, status, disclosure, and application-profile state before sending.
Multiple reader-authentication statements retain their independent
`authenticationIndex`, and holder-key authorization is reported per document
request so mixed signature/MAC responses cannot be collapsed into one prompt.

Reader authentication validity does not establish reader trust. To require a
trusted reader, provision Reader CA certificates out of band and pass the shared
evaluator explicitly:

```kotlin
val readerTrust = MobileWalletProximityConfiguredReaderTrustEvaluator(
    MobileWalletProximityReaderTrustConfiguration(
        trustAnchors = listOf(
            MobileWalletProximityReaderTrustAnchor(
                certificateDerBase64Url = readerCaDerBase64Url,
                displayName = "Example reader authority",
            )
        ),
        revocationPolicy = MobileWalletProximityReaderRevocationPolicy.Check(
            applicationRevocationEvaluator
        ),
    )
)
val configuration = MobileWalletProximityConfiguration(
    readerPolicy = MobileWalletProximityReaderPolicy.RequireTrusted,
    readerTrustEvaluator = readerTrust,
)
```

The SDK performs certificate profile, time, and explicit-anchor path validation,
but performs no hidden network lookup and ships no reader trust list. A root sent
by the reader is only path evidence; it is never trusted unless the application
provisioned the same certificate. Optional RICAL providers use separate explicit
provider roots, signer policy, revocation, and constraint boundaries. Demo apps
can pass a named test anchor through the same configuration constructor, but
test anchors must not become production defaults.

Wallet applications that let holders manage this policy can persist a canonical
`MobileWalletProximityReaderTrustSettings` snapshot. Use
`MobileWalletProximityReaderTrustSettingsCodec.prepareImport` to validate and
preview public trust material before saving the returned settings. The importer
accepts DER or certificate-only PEM Reader CAs and versioned walt.id JSON trust
bundles containing named Reader CAs and static signed RICAL configuration. It
rejects private keys, PKCS#12/PFX files, unknown JSON fields or versions,
duplicates, non-CA or expired anchors, invalid RICAL signatures and paths, and
files larger than 1 MiB. The codec performs no persistence or network access.

The version-1 bundle shape is deliberately narrow; every encoded value is
unpadded Base64URL and unknown fields are rejected:

```json
{
  "version": 1,
  "type": "org.waltid.wallet.reader-trust",
  "readerAuthorities": [
    {
      "name": "Example Reader CA",
      "certificateDerBase64Url": "<public DER certificate>"
    }
  ],
  "ricalProviders": [
    {
      "providerId": "<RICAL provider identifier>",
      "acceptedTypes": ["<RICAL type>"],
      "providerTrustAnchorsDerBase64Url": ["<public DER certificate>"],
      "acceptedSignerCertificatePolicyOids": ["<certificate-policy OID>"],
      "establishReaderTrust": false,
      "signedRicalBase64Url": "<untagged COSE_Sign1>"
    }
  ]
}
```

Read one immutable settings snapshot when a new session starts and apply it with
`MobileWalletProximityReaderTrustSettings.applyTo`. Settings changed during a
session therefore affect only the next session. The demo wallets expose this as
**Settings → Credential Sharing → Reader Authentication** and store only the
canonical public configuration in app-private storage.

Only one proximity session may be active per wallet. Always call `close()` when
the journey leaves the screen; closing and cancellation are idempotent and every
new session creates fresh engagement identifiers and ephemeral key material.

## Persistence and encryption

`MobileWalletConfig()` uses managed encrypted SQLDelight persistence by default on Android and iOS. Normal SDK users do not provide a database key: the SDK generates one per wallet database, stores it in platform-protected storage, and uses SQLCipher for the local wallet database.

Managed signing keys are device-local by default. They protect data at rest on the current device, but they are not a cross-device recovery mechanism. Use `MobileWalletDatabaseKey.Provided` when an app needs enterprise/KMS ownership or recoverable database-key material. Credential and DID store overrides are independent; signing keys always remain platform-managed. Supported mobile platforms intentionally do not fall back to plaintext wallet databases.

`MobileWalletConfig()` does not accept any OpenID4VP `transaction_data` profiles by default. Wallet apps must pass the profile types they understand through `transactionDataProfiles`; requests containing unknown transaction data types are rejected before the user can submit a presentation. Profile fields are preserved for app UI and display metadata.

The examples below build `MobileWalletConfig` values. Pass the selected config
to `MobileWalletFactory(...).create(config)` from a coroutine to create the
wallet.

Use the encrypted default when the app does not need custom persistence:

<!-- doc-snippet:start kotlin-default-persistence -->
```kotlin
val config = MobileWalletConfig(walletId = "consumer-wallet")
```
<!-- doc-snippet:end kotlin-default-persistence -->

Provide database keys while keeping SDK SQLDelight stores by implementing `DatabaseEncryptionKeyProvider`:

<!-- doc-snippet:start kotlin-provided-database-key -->
```kotlin
class KmsDatabaseKeyProvider : DatabaseEncryptionKeyProvider {
    override suspend fun getOrCreateKey(walletId: String, databaseName: String): DatabaseEncryptionKey {
        val keyBytes = loadOrCreateKeyBytes(walletId, databaseName)
        return DatabaseEncryptionKey(keyId = "$walletId:$databaseName", material = keyBytes)
    }

    override suspend fun deleteKey(walletId: String, databaseName: String) {
        deleteKeyBytes(walletId, databaseName)
    }
}

val config = MobileWalletConfig(
    walletId = "consumer-wallet",
    persistence = MobileWalletPersistence(
        databaseKey = MobileWalletDatabaseKey.Provided(
            provider = KmsDatabaseKeyProvider()
        )
    )
)
```
<!-- doc-snippet:end kotlin-provided-database-key -->

This mode is covered by Android device and iOS simulator integration tests so
provider lookup, encrypted database reopening, and provider deletion stay wired
to the real platform drivers.

Override only credential storage while retaining the default encrypted database, database-key ownership, DID store, and platform signing-key store:

<!-- doc-snippet:start kotlin-custom-credential-store -->
```kotlin
val config = MobileWalletConfig(
    walletId = "consumer-wallet",
    persistence = MobileWalletPersistence(
        credentialStore = appCredentialStore
    )
)
```
<!-- doc-snippet:end kotlin-custom-credential-store -->

KMP consumers can override credential and DID storage while signing keys remain platform-managed:

<!-- doc-snippet:start kotlin-store-overrides -->
```kotlin
val config = MobileWalletConfig(
    walletId = "consumer-wallet",
    persistence = MobileWalletPersistence(
        credentialStore = appCredentialStore,
        didStore = appDidStore,
    )
)
```
<!-- doc-snippet:end kotlin-store-overrides -->

Call `MobileWallet.deleteWallet()` to delete local wallet material for a wallet: stored key descriptors, credentials, DIDs, platform signing keys, encrypted database files and sidecars, and the configured database key. Credential and DID custom stores receive the corresponding remove calls.

If a local development build has an old plaintext database or a database restored without its matching key, opening the wallet can fail with a typed storage error. Reset local state by calling `deleteWallet()`, uninstalling the app, or deleting the app's local wallet data. WAL-1085 does not perform plaintext-to-encrypted migration.

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
