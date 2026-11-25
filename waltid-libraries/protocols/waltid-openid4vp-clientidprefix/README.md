<div align="center">
<h1>OpenID4VP Client ID Prefix Helper</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Parsing and authentication utilities for OpenID4VP client identifier prefixes</p>

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

Helper library for parsing and authenticating OpenID4VP client identifier prefixes as defined in Section 5.9 of the OpenID4VP 1.0 specification. This library enables dynamic Verifier identification without pre-registration.

## Main Purpose

OpenID4VP allows Verifiers to identify themselves using prefixes beyond simple client IDs. This library provides parsing and authentication for all supported prefix types, enabling:
- Dynamic registration without pre-registration
- Trust mechanisms using DIDs, X.509 certificates, or attestations
- Federation via OpenID Federation

## Supported Prefixes

| Prefix | Example | Authentication Method |
|--------|---------|----------------------|
| `redirect_uri:` | `redirect_uri:https://verifier.com/callback` | Redirect URI trust |
| `x509_san_dns:` | `x509_san_dns:verifier.example.com` | X.509 certificate verification |
| `decentralized_identifier:` | `decentralized_identifier:did:key:z6M...` | DID verification |
| `verifier_attestation:` | `verifier_attestation:...` | Verifier Attestation JWT |
| `openid_federation:` | `openid_federation:...` | OpenID Federation resolution |

## Usage

### Parsing Client IDs

```kotlin
import id.walt.openid4vp.clientidprefix.ClientIdPrefixParser

val parseResult = ClientIdPrefixParser.parse("decentralized_identifier:did:example:12345")

parseResult.fold(
    onSuccess = { clientId ->
        println("Prefix: ${clientId.prefix}")
        println("Identifier: ${clientId.identifier}")
    },
    onFailure = { error ->
        println("Parsing failed: ${error.message}")
    }
)
```

### Authentication

```kotlin
import id.walt.openid4vp.clientidprefix.*

val clientId = ClientIdPrefixParser.parse(rawClientId).getOrThrow()
val requestContext = RequestContext(
    clientId = rawClientId,
    requestObjectJws = signedRequestJwt,
    clientMetadataJson = metadataJson
)

when (val result = ClientIdPrefixAuthenticator.authenticate(clientId, requestContext)) {
    is ClientValidationResult.Success -> {
        println("✅ Verifier authenticated")
        // Use result.clientMetadata for verifier information
    }
    is ClientValidationResult.Failure -> {
        println("❌ Authentication failed: ${result.error.message}")
    }
}
```

### X.509 trust

`x509_san_dns` and `x509_hash` fail closed unless `ClientIdTrustConfiguration` names a trust source.
When both are set, the registry alone decides those prefixes, so a registry denial never falls through
to a pinned anchor:

- `x509TrustAnchors`: a static `X509CertificateTrustStore`. The chain is validated against it (PKIX).
- `x509TrustRegistry`: a `ClientIdX509TrustRegistry` around a `TrustDecisionEvaluator` from
  `waltid-trust-decision`. The wallet checks that `x5c` is one leaf-first chain, that every certificate
  is within its validity period, the leaf profile, the request signature and the client id binding. Then
  it asks the evaluator about the full `x5c` chain for `WALLET_PRESENTATION_AUTH_REQUEST`.
  The decision must meet `TrustRequirement.RegistryFresh`: `TRUSTED` for a relying-party entity type,
  from an accepted, fresh source. An `UNKNOWN` decision fails with `X509TrustSourceUnavailable`, any
  other denial with `X509TrustAnchorMismatch`.

```kotlin
val trust = ClientIdTrustConfiguration(
    x509TrustRegistry = ClientIdX509TrustRegistry(
        evaluator = HttpTrustDecisionEvaluator("https://trust.example.com/resolve/certificate-chain"),
    ),
)
```

A `verifier_attestation` signed with `x5c` is checked only against `x509TrustAnchors`, with or without a
registry.

## Related Libraries

- **[waltid-openid4vp](../waltid-openid4vp/README.md)** - Core OpenID4VP models
- **[waltid-openid4vp-wallet](../waltid-openid4vp-wallet/README.md)** - Wallet implementation using this library

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
