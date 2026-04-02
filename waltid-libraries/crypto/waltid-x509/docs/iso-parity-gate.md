# ISO parity gate for generic issuance

The generic/profile-driven JVM issuer exposes `iso.iaca` and `iso.document-signer`, but deprecating the dedicated ISO implementation is gated intentionally.

## Current rule

The ISO-specific builder/parser/validator path remains authoritative until the generic path is proven equivalent enough in behavior.

## Minimum parity proof before deprecation

Before deprecating `IACACertificateBuilder`, `IACACertificateParser`, `IACAValidator`, `DocumentSignerCertificateBuilder`, `DocumentSignerCertificateParser`, or `DocumentSignerValidator`, all of the following should be true for `iso.iaca` and `iso.document-signer`:

- Generic issuance delegates to, or stays semantically aligned with, the existing ISO-specific issuance logic.
- Tests compare decoded semantics, not only raw DER bytes.
- Parity coverage includes subject, issuer, validity, key usage, EKU where applicable, basic constraints, issuer/subject alternative names where applicable, and CRL distribution points where applicable.
- Certificates emitted through the generic path are accepted by the existing ISO validators.
- Roundtrip parsing and practical chain-validation behavior stay acceptable for the tested ISO flows.
- Any remaining mismatch is documented explicitly and judged non-blocking.

## Why this gate exists

The generic profile layer is broader than the current ISO-specific implementation. Deprecating the ISO-specific classes too early would risk losing profile-specific behavior that is already relied upon. The safer next step is to keep the dedicated ISO code authoritative while parity tests continue to prove that the generic path matches it closely enough.
