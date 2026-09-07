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

Reader-authentication scope is either `Document(index)` or `WholeRequest`; only a valid authentication
result carries verified evidence and an application trust decision. Consent previews, capability
snapshots, and trust policy retain owned collections and return detached collection views. Request
contexts reconstruct decoded projections from their original exact bytes, preserving signed and
transcript encodings when a consumer modifies a projection.

The deterministic loopback fixtures live in `src/commonTestFixtures/kotlin` and are included only by
consumer test source sets. Production transport kinds describe actual supported bearers.
