# waltid-mdoc-proximity

Pure Kotlin Multiplatform holder protocol for ISO mdoc proximity presentation.

The module owns session establishment and encryption, immutable request/consent binding, repeated
exchange state, limits, timeouts, normalized errors, reader evidence/trust seams, and transport
contracts. It has no Android or Apple radio API, wallet store, application lifecycle, or UI dependency.

Wallet-owned application profiles may supply versioned, locally validated, display-safe authorization
details plus an opaque profile-result digest. The engine carries and binds that data through consent
and submission without interpreting application extensions or introducing application-specific types.

Transport implementations exchange complete bounded messages through the walt-owned SPI. Platform
adapters must treat the message bytes as opaque and keep ISO parsing, cryptography, credential choice,
trust, and disclosure decisions in common code.
