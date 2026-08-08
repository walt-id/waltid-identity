<div align="center">
<h1>walt.id Relying Party Registration Certificate</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Issuance, parsing and wallet-side verification of EUDI Wallet-Relying Party Registration Certificates (WRPRC)</p>

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

Multiplatform library for Wallet-Relying Party Registration Certificates (WRPRC, JWT type `rc-wrp+jwt`), as
specified in [ETSI TS 119 475](https://www.etsi.org/deliver/etsi_ts/119400_119499/119475/01.02.01_60/ts_119475v010201p.pdf)
clause 5.2.4. It covers both sides of the certificate's lifecycle:

- **Issuance** - build and sign a registration certificate JWT for a Relying Party (Verifier).
- **Wallet-side verification** - decode and verify a registration certificate, and check whether it actually
  authorizes the credentials/claims a Verifier is requesting in an OpenID4VP DCQL query.

## Main Purpose

Under the EUDI Wallet ecosystem, a Relying Party is not automatically trusted just because it presents a valid
X.509 certificate. A Wallet-Relying Party Registration Certificate is a registrar-issued attestation - bound to the
Relying Party's certificate chain - that states *what* the Relying Party is registered to request (which credential
types, and optionally which specific claims) and *why* (its purpose and entitlements). Wallets use it to reject
requests that ask for more than a Verifier is registered for, even if the request itself is otherwise well-formed.

This library provides:

- `RelyingPartyRegistrationCertificate` - the certificate payload data model (Table 7 of the spec, plus the
  credential/optional attribute tables).
- `RelyingPartyRegistrationCertificateIssuer` - signs a payload into an `rc-wrp+jwt` JWT, bound to an `x5c`
  certificate chain.
- `RelyingPartyRegistrationCertificateVerifier` - decodes and fully verifies a certificate JWT (signature, `x5c`
  chain trust, `iat`/`exp` validity).
- `RegistrationCertificateDcqlMatcher` - checks whether a certificate's registered credentials/claims cover a DCQL
  query.
- `RegistrationCertificateWalletValidator` - wallet-side entry point tying the above together: verify a certificate
  and match it against an OpenID4VP `AuthorizationRequest` in one call.

For a ready-to-use command-line front end built on this library, see
[waltid-rp-certificate-cli](../../../waltid-applications/waltid-rp-certificate-cli).

## Usage

### Issuing a registration certificate

```kotlin
import id.walt.rpcert.issuance.RelyingPartyRegistrationCertificateIssuer
import id.walt.rpcert.models.*

val payload = RelyingPartyRegistrationCertificate(
    name = "Example Bank AG",
    sub = "EUID:ATU12345678",
    country = "AT",
    registryUri = "https://registry.example.at",
    srvDescription = listOf(listOf(MultiLangString("en", "Account opening identification"))),
    entitlements = listOf("Service_Provider"),
    privacyPolicy = "https://bank.example.com/privacy",
    supervisoryAuthority = SupervisoryAuthority(email = "office@dsb.gv.at"),
    iat = System.currentTimeMillis() / 1000,
    purpose = listOf(MultiLangString("en", "Identify customers for account opening")),
    credentials = listOf(
        RegistrationCertificateCredential(
            format = CredentialFormat.MSO_MDOC,
            meta = MsoMdocMeta(doctypeValue = "eu.europa.ec.eudi.pid.1"),
            claim = listOf(
                Claim(path = listOf(JsonPrimitive("eu.europa.ec.eudi.pid.1"), JsonPrimitive("given_name"))),
            ),
        ),
    ),
)

// signingKey must match the public key of x5c's leaf certificate (leaf first, DER, base64-encoded)
val certificateJwt = RelyingPartyRegistrationCertificateIssuer.issue(
    key = signingKey,
    x5c = listOf(leafCertificateBase64, rootCertificateBase64),
    payload = payload,
)
```

### Wallet-side verification and DCQL matching

```kotlin
import id.walt.rpcert.wallet.RegistrationCertificateWalletValidator
import id.walt.rpcert.wallet.RegistrationValidationResult

val result = RegistrationCertificateWalletValidator.validate(
    authorizationRequest = authorizationRequest, // parsed OpenID4VP AuthorizationRequest
    registrationCertificateJwt = certificateJwt,
    trustAnchors = listOf(trustedRootDer),
)

when (result) {
    is RegistrationValidationResult.Allowed ->
        println("Allowed: ${result.registrationCertificate.certificate.name} may request this")

    is RegistrationValidationResult.RequestNotCovered ->
        println("Rejected: registration certificate does not cover the requested claims")

    is RegistrationValidationResult.InvalidRegistrationCertificate ->
        println("Rejected: certificate invalid - ${result.cause.message}")

    is RegistrationValidationResult.MissingDcqlQuery ->
        println("Rejected: ${result.message}")
}
```

`RelyingPartyRegistrationCertificateVerifier.verify` and `RegistrationCertificateDcqlMatcher.matchDcqlQuery` are
also available directly if you need to verify a certificate or match a DCQL query on their own, without going
through the combined validator.

## Related Libraries

- **[waltid-rp-certificate-cli](../../../waltid-applications/waltid-rp-certificate-cli)** - command-line tool for issuing and validating registration certificates using this library
- **[waltid-openid4vp](../waltid-openid4vp/README.md)** - core OpenID4VP models, including DCQL queries matched against by this library
- **[waltid-dcql](../../credentials/waltid-dcql)** - Digital Credentials Query Language models
- **[waltid-x509](../../crypto/waltid-x509)** - X.509 certificate chain building and validation used for the `x5c` header
- **[waltid-crypto](../../crypto/waltid-crypto)** - key management and JWS signing/verification

## Join the community

- Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
- Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
- Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
