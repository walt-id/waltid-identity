# waltid-mdoc-proximity

Pure Kotlin Multiplatform holder protocol for ISO mdoc proximity presentation.

The module owns session establishment and encryption, immutable request/consent binding, repeated
exchange state, limits, timeouts, normalized errors, reader evidence/trust seams, and transport
contracts. It has no Android or Apple radio API, wallet store, application lifecycle, or UI dependency.

Transport implementations exchange complete bounded messages through the walt-owned SPI. Platform
adapters must treat the message bytes as opaque and keep ISO parsing, cryptography, credential choice,
trust, and disclosure decisions in common code.
