# Historical ITB baseline — 2026-09-22

This records the initial deployed baseline, not current qualification. See
[WAL-1423](https://linear.app/walt-new/issue/WAL-1423/itb-initial-tests) for run evidence
and the [operator guide](ITB-WALLET.md) for current execution and boundaries.

At commit `7d5eb3b3d79cee35d445627cb6edd65e4943a3c1`, the runner executed all
21 deployed cases: **11 wallet passes and 10 wallet failures**, with a correlated
terminal ITB report for every case. There were no orchestration, cleanup or
skipped-case results. The run used synthetic wallet data and preceded the
runner's key-attestation fixture, encrypted Request Object support and native
payment authorizer.

Three observations explain the original failures and the retained regression coverage:

- Signed DC API dispatch and mdoc ES256 negotiation depended on
  [Identity #2141](https://github.com/walt-id/waltid-identity/pull/2141), which has
  since merged. The runner supplies its pinned reference CA to that path.
- VCI005 and VCI008 returned malformed mdoc COSE `kid` values: text strings where
  [RFC 9052 section 7.1](https://www.rfc-editor.org/rfc/rfc9052.html#section-7.1)
  requires byte strings. This was reported as
  [ITB #49](https://github.com/webuild-consortium/wp4-interop-test-bed/issues/49).
  Targeted runs showed **wallet parsing failure alongside ITB `SUCCESS`**.
  The runner therefore requires both wallet success and terminal ITB success;
  regression tests retain this distinction without weakening COSE validation.
- Ordinary TS12 payment requests required request-decryption keys in POST wallet
  metadata. The runner now uses exchange-specific keys and validates the signed
  inner request after decryption. This is separate from response encryption.

The historical results establish neither current deployment behavior nor native
consent, production attestation or full SCA assurance. Current reports identify
the source revision, selected cases, wallet outcomes and correlated ITB sessions.
