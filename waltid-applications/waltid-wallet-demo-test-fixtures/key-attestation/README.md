# Shared demo key attestation

Compose Android/iOS and native Swift iOS select these test attesters automatically,
only when issuer metadata requires key attestation:

| Exact credential issuer | Demo attester |
| --- | --- |
| `https://dev-i4mlab.aegean.gr/rfc-issuer` | Local ITB synthetic ES256 attestation |
| `https://issuer.eudiw.dev` | EUDI public mock Wallet Provider |

Unknown issuers requiring attestation fail. Provider failures and cancellation
propagate without fallback. No selector or startup network request is needed;
wallet factories reattach the resolver after recreation. The SDK verifies the
signature, proof key, nonce, lifetime and advertised assurance constraints before
sending the proof. Each proof collection retains its provider and verification key.

## Configuration and consumers

[KeyAttestationProfiles.json](Resources/KeyAttestationProfiles.json) is the single
source for issuer routing, endpoints, algorithms, synthetic claims and lifetimes.
Both adapters forward its ITB claims and supply the actual proof key, nonce and
fresh expiry values. The profile contains no signing keys or JWTs.

- `kotlin/` is compiled into the Compose demo, ITB runner and Android SDK device tests.
  `sources.gradle.kts` embeds the profile into generated sources in each build directory.
- The private `WalletDemoKeyAttestation` Swift package owns `swift/` and bundles the
  same profile for the native demo and its SDK integration tests.
- App UI tests use the normal demo factories. Tests creating wallets directly
  attach the shared resolver; rejection tests retain their invalid providers.
- Kotlin resolver contract tests live in the ITB runner's ordinary JVM test suite.
  The native test suite checks the independent CryptoKit signer.

SDK production sources contain only the generic provider/resolver contracts.

## Assurance and scope

Both integrations provide test assurance. ITB generates a fresh signing key for
each proof collection and makes explicitly unassessed `example.invalid` claims;
it cannot satisfy certified storage or authentication requirements. This feature
is separate from `ATTESTATION_*` client authentication and production WUA.

The additional WE BUILD pilot attester is deferred until the
[Wallet Provider service (WAL-1471)](https://linear.app/walt-new/issue/WAL-1471)
is available, with mobile integration through
[WAL-1479](https://linear.app/walt-new/issue/WAL-1479).
