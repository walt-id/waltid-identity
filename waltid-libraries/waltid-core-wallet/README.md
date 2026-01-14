<div align="center">
<h1>walt.id Core Wallet</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Kotlin Multiplatform core library for building wallets using OpenID4VC/VP</p>

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

## What This Library Contains

The Core Wallet provides high-level building blocks to implement wallet functionality based on the OpenID family of protocols:
- OpenID for Verifiable Credential Issuance (OpenID4VCI) — receive credentials from issuers
- OpenID for Verifiable Presentations (OpenID4VP) — present credentials to verifiers

It wraps the lower‑level primitives from `waltid-openid4vc` and adds opinionated flows that are convenient for app developers.

### Further information

Checkout the documentation regarding [issuance](https://docs.walt.id/concepts/data-exchange-protocols/openid4vci), [presentation](https://docs.walt.id/concepts/data-exchange-protocols/openid4vp) and [our wallet API implementation](https://docs.walt.id/community-stack/wallet/getting-started) to find out more.

## Main Purpose

Use this library to add wallet capabilities to your application without re‑implementing protocol flows. It focuses on:
- Orchestrating the VCI (issuance) steps end‑to‑end
- Orchestrating the VP (presentation) steps with minimal boilerplate
- Handling practical details (e.g., direct_post delivery, basic presentation submission mapping)

For full protocol models and advanced controls, see `waltid-openid4vc` and the OpenID4VP libraries.

## Key Concepts

- Credential Offer (VCI): Encodes what the issuer offers; the wallet resolves it and requests tokens/credentials
- Proof of Possession (VCI): Holder signs a proof JWT (or other proof type) bound to c_nonce and key id
- Authorization Request (VP): Verifier’s request the wallet parses and responds to (redirect or direct_post)
- Presentation Result (VP): Wallet builds a VP and a presentation submission mapping for the verifier

## Assumptions and Dependencies

- Multiplatform: JVM, JS, and iOS (enable iOS by setting Gradle property `enableIosBuild=true`)
- Depends on: `waltid-openid4vc`, `waltid-crypto`, `waltid-w3c-credentials`, `waltid-mdoc-credentials`, `waltid-sdjwt`
- Network: Uses Ktor client for HTTP interactions

## How to Use This Library

### OpenID4VCI (Issuance) — Basic Workflow

1) Resolve the credential offer and provider metadata
2) Request a token (pre-authorized or authorization code, depending on the offer)
3) Build Proof of Possession (PoP) for the request
4) Request the credential(s) and verify the response

```kotlin
// 1) Resolve offer and metadata
val offer = CoreWalletOpenId4VCI.resolveCredentialOffer(credentialOfferUrl)
val providerMetadata = CoreWalletOpenId4VCI.getProviderMetadataForOffer(offer)

// 2) Token request (pre-authorized code example)
val token = CoreWalletOpenId4VCI.requestToken(offer, providerMetadata)

// 3) Build PoP (JWT proof) bound to c_nonce and holder key id
val proof = CoreWalletOpenId4VCI.signProofOfPossession(
    holderKey = holderKey,
    credentialIssuerUrl = providerMetadata.credentialIssuer!!,
    clientId = clientId,
    nonce = token.cNonce,
    proofKeyId = "$holderDid#${holderKey.getKeyId()}"
)

// 4) Request offered credentials
val offered = CoreWalletOpenId4VCI.getOfferedCredentials(offer, providerMetadata)
val responses = offered.map { oc ->
    CoreWalletOpenId4VCI.receiveCredential(
        offeredCredential = oc,
        proofOfPossession = proof,
        tokenResponse = token,
        providerMetadata = providerMetadata
    ).also { CoreWalletOpenId4VCI.checkReceivedCredential(it) }
}
```

Or use the end‑to‑end helper:

```kotlin
val responses = CoreWalletOpenId4VCI.fullIssuanceReceivalFlow(
    resolvedOffer = offer,
    key = holderKey,
    did = holderDid,
    clientId = clientId
)
```

### OpenID4VP (Presentation) — Basic Workflow

1) Parse the authorization request URL
2) Select credentials and build a Verifiable Presentation (VP)
3) Generate the protocol response and deliver it (direct_post or redirect)

```kotlin
val httpResponse = CoreWalletOpenId4VP.presentCredential(
    presentationRequestUrl = openid4vpUrl,
    signVpCallback = { authzRequest ->
        // Select credentials based on the request; build VP JWT
        val vpJwt = PresentationBuilder()
            .apply {
                did = holderDid
                nonce = authzRequest.nonce
                credentials.forEach { addCredential(JsonPrimitive(it)) }
            }
            .buildAndSign(holderKey)

        // Map VP -> PresentationSubmission for the Presentation Definition
        val pd = OpenID4VP.resolvePresentationDefinition(authzRequest)
        val submission = CoreWalletOpenId4VP
            .makePresentationSubmissionForPresentationDefinition(pd, credentials)

        PresentationResult(listOf(JsonPrimitive(vpJwt)), submission)
    }
)
```

Notes:
- `presentCredential` will deliver using `direct_post` when `response_uri` is present; otherwise it computes a redirect URL.
- The included presentation‑submission mapping is a pragmatic helper and may be replaced by dedicated libraries when available.

## When to Use Which Library

- Use `waltid-core-wallet` to quickly integrate issuance and presentation flows with minimal wiring
- Use `waltid-openid4vc` for lower‑level protocol models and full control
- Use `waltid-openid4vp` / `waltid-openid4vp-wallet` for the 1.0 VP flow with DCQL matching

## Related Libraries

- `waltid-openid4vc` — Core models and helpers for VCI/VP
- `waltid-openid4vp` — Core models for OpenID4VP 1.0
- `waltid-openid4vp-wallet` — Wallet presenter for OpenID4VP 1.0
- `waltid-digital-credentials` — Unified parsing of W3C/SD‑JWT/mdoc

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>
