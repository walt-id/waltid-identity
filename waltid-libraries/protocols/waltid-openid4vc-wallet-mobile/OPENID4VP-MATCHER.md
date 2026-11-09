# Vendored OpenID4VP Credential Manager matcher

`src/androidMain/assets/id/walt/wallet2/mobile/openid4vpmatcher.wasm` is the Credential Manager
matcher this wallet registers for its OpenID4VP registry, in place of the one AndroidX embeds in
`OpenId4VpRegistry`. AndroidX serializes the credential records and icon region. The adapter preserves that region and
extends the JSON document with `walt_payment_instructions_v1`, consumed only by our patched matcher.

This is separate from the Annex C matcher described in `ANNEX-C-MATCHER.md`. The two registries are
registered independently and share no code beyond both being raw `DigitalCredentialRegistry`
subclasses.

## Why it is vendored

AndroidX's embedded matcher cannot report a transaction data prompt and a second credential from the
same request. Given a valid OpenID4VP request for two DCQL credentials with one `transaction_data`
entry bound to one of them, it reports zero candidates and the picker shows "Your info wasn't found".

Once `transaction_data` is present it takes a payment-only emit path, and on that path a candidate the
transaction data does not name emits nothing at all — there is no fallthrough to the ordinary
verification-entry path. The option has meanwhile been declared to the platform with arity 2, so
declared-two-delivered-one makes the platform discard the whole option: the failure is total rather
than degrading to the payment credential alone. It is specific to the transaction data reporting path,
not to generic multi-credential DCQL matching. The Rust matcher decides per credential instead,
reporting the named credential as a payment entry and every other credential in the option as a
standard verification entry.

Not fixable by upgrading: at the time of vendoring, both the newest published
`registry-digitalcredentials-openid` and `androidx-main` HEAD embedded a byte-identical matcher. Nor by
substituting the Annex C matcher we already vendor, which implements no `transaction_data` at all.

## Provenance

The committed binary is **our build of upstream sources with the local payment patch**, not an artifact Google publishes:
upstream ships the Rust sources only.

| | |
| --- | --- |
| Upstream repository | https://github.com/android/identity-samples |
| Branch | `wasm` |
| Commit | `5d966fc4913cac93f3b3b11e11bdd44d3e0b5c9e` |
| Upstream path | `CredentialProvider/wasm/matcher-rs` |
| Source modifications | `matcher-patches/5d966fc4-local-ts12-payment-confirmation.patch` |
| Built artifact | `target/wasm32-unknown-unknown/release/presentation.wasm`, built here |
| Size | 191,162 bytes |
| SHA-256 | `9bd1b41f515047e1bc8b2737a1f81f8dbf7cb067852a9ed57ae2be7ce96de8d5` |
| Toolchain | `rustc 1.99.0-nightly (12c36e253 2026-08-10)`, target `wasm32-unknown-unknown` |
| License | Apache-2.0 |

`NOTICE-openid4vpmatcher.txt` ships beside the binary and records the licenses of the crates compiled
into it.

## Rebuilding and updating

The procedure below is how to **update** the binary, not a way to reproduce the committed SHA-256. The
build is not bit-reproducible across toolchains: `build.sh` uses `cargo +nightly` with `-Z build-std`,
so the bytes depend on the nightly in use. The committed bytes came from the toolchain recorded in the
provenance table above; a different nightly will produce a different, equally valid binary with a
different hash.

```shell
git clone https://github.com/android/identity-samples
git -C identity-samples checkout 5d966fc4913cac93f3b3b11e11bdd44d3e0b5c9e
git -C identity-samples apply <this-module>/matcher-patches/5d966fc4-local-ts12-payment-confirmation.patch
cd identity-samples/CredentialProvider/wasm/matcher-rs
rustup toolchain install nightly --component rust-src --target wasm32-unknown-unknown
cargo test --lib
bash build.sh
cp target/wasm32-unknown-unknown/release/presentation.wasm \
  <this-module>/src/androidMain/assets/id/walt/wallet2/mobile/openid4vpmatcher.wasm
```

So the maintenance model is:

- **The committed binary**: the SHA-256 that `AndroidVendoredMatcherTest` pins verifies its identity, so
  an accidental swap cannot go unnoticed. The runtime compatibility assumptions below still have to hold.
- **An intentional update** picks an upstream commit, builds it with a toolchain recorded here, runs
  upstream's own test suite at that commit, runs the Android matcher regressions in
  `DigitalCredentialSharingE2ETest`, and then updates the asset, the pinned hash and this table
  together. Updating the binary without repinning the hash fails the test by design.

## Compatibility contract

Unlike the Annex C matcher, this one consumes the *AndroidX* registry format and emits the AndroidX
`metadata` channel, so `parseMatcherSelection`'s existing `req:<n>;…` handling applies unchanged and no
Multipaz-private convention is involved. Three things have to hold. No full behavioural parity audit
against AndroidX's matcher was performed, so this is not a closed list:

- **Protocol scoping.** The matcher iterates `registry.supported_protocols` and skips any request
  whose protocol is not listed. The SDK always registers `openid4vp-v1-signed` and
  `openid4vp-v1-unsigned`. Multisigned stays
  off the list: the matcher recognises all three `openid4vp-v1-*` protocols, and would
  serve multisigned requests the wallet cannot fulfill if the registry advertised them.
  `DigitalCredentialSharingE2ETest.doesNotSurfaceForMultisignedRequests` asserts the multisigned
  exclusion against the real platform.
- **Host imports.** The matcher imports its host functions from module `credman`, whereas AndroidX's C
  matcher imports the set-based ones from `credman_v2` and `credman_v5`. Google Play services resolves
  both, but a future GMS that stops aliasing `credman` for those would break this binary and not
  AndroidX's.
- **Optional credential sets are dropped.** One known behaviour difference: the matcher never evaluates
  `credential_sets` entries with `required: false`, so a credential only an optional set asks for
  contributes no candidates. AndroidX's C matcher reports them. Nothing in this repo sends optional sets
  and no test covers them, so check this before sending one to an Android holder.

## Removing it

Delete the asset, the notice, `AndroidOpenId4VpRegistry`, and its branch in
`AndroidDigitalCredentialRegistry.replace`, as soon as a released
`androidx.credentials.registry:registry-digitalcredentials-openid` embeds a matcher that reports a
payment entry and a standard entry from one request. Test for that with
`DigitalCredentialSharingE2ETest.sharesMdocWithScaPaymentTransactionDataAndSecondCredential`: drop the
custom registry and see whether it still passes.

Because the matcher is vendored rather than declared as a dependency, ordinary dependency-update
tooling will not detect upstream changes to it.

## TS-12 system payment confirmation

The local patch supports one unscheduled `urn:eudi:sca:payment:1` transaction, including ordinary
credentials requested alongside it. It expands the original JSON number token without floating point
and pads to the ISO 4217 minor-unit count used by the shared wallet validator. It never rounds, uses
verifier-supplied `amount_display`, or changes the original transaction bytes retained for proof binding.
The locked `serde_json` parser preserves arbitrary-precision number tokens. Android supplies decimal
separators and digit symbols for the preferred language ranges, without parsing the amount as a
platform floating-point number (for example `KWD 1.234` in English and `KWD 1,234` in German).

For SD-JWT payment candidates, `PaymentConsentResolver` authenticates the stored attestation using
independently configured issuer keys before retrieving its issuer-designated metadata. The shared
metadata parser validates labels and exports language-indexed instructions. Registration includes
transaction labels, UI labels and requested-attribute labels from that authenticated document; it never
copies verifier text into issuer instructions. The matcher requires labels for every applicable
parameter and requested attribute, including a common preferred language. It displays levels 1–3 in
the multiline subtitle and omits level 4. Optional descriptions, title and security hint are shown only
when the issuer supplied them. Request values are isolated for bidirectional text and control characters
are escaped to prevent injected instruction lines. Unsigned warning text and Boolean translations are
wallet-owned English/German text; unsupported languages fail closed when these are needed.

Payment entry IDs bind the authenticated document, credential wire representation and locale preferences.
The wallet verifies the Request Object first, then re-resolves metadata and rejects a stale system
selection before disclosure. Ordinary disclosure does not fetch payment metadata. Unavailable or
untrusted metadata leaves ordinary registration usable but supplies no SD-JWT payment candidate.
Unsupported metadata paths, missing required labels, multiple transactions, scheduled/recurring
payments and unknown payment extensions produce no payment candidate. This filtering happens before
the provider opens, including when wallet preview is enabled; the direct wallet-owned presentation
route is unchanged. On the tested Google Play services version, an empty match entered the cross-device
hybrid activity and left the caller pending, including with `preferImmediatelyAvailableCredentials=true`.
Google documents that Digital Credentials has no equivalent of the immediate-availability option:
https://developer.android.com/identity/digital-credentials/email-verification-implementation.
The physical Android 12 / Play services 26.42.31 run reproduced the hybrid activity. Back and caller
cancellation alone did not close it. Its explicit **Cancel** action did return cancellation and pass
cleanup, independently for multiple transactions and missing labels. The focused device helper now
accepts either the empty state's Close action or the QR fallback's Cancel action. This is a
user-dismissed fallback, not an immediate no-credential callback. The matcher returns normally with zero entries, and the wallet provider has not been invoked;
it cannot explain or replace that system fallback. Keep fail-closed matching rather than fabricating
a selectable credential to carry an error message. The existing mdoc payment-summary
route retains its scope and gains exact amount formatting; it has no authenticated TS-12 label chain
and must not be described as equivalent to the SD-JWT route.

This remains a system-sheet improvement, **not full TS-12 display conformance**. Observed on Android 17
(API 37), Play services 26.43.30, ABI 9: payment entries display multiline subtitles, but ignore the
ordinary field, metadata-text and warning/disclaimer APIs. The platform controls the heading and action
labels; it cannot express issuer-prescribed button labels, level-1 emphasis or a separate level-3 details
hierarchy through the tested ABI. The subtitle warning plus system Continue is not established as a
separate TS-12 unsigned-request consent step. Signed Request Objects are decoded for matching but
are authenticated only by the wallet after selection; the sheet cannot certify their validity.
These require a Google platform enhancement or an explicit product-policy decision. Wallet preview
remains user-controlled; its existing issuer-led review and native signing authorization are preserved.

Run `node matcher-tests/payment.mjs` to exercise the actual vendored binary, including exact decimal
vectors, multilingual labels, selected attributes, ordinary companion entries, signed/unsigned routing,
missing instructions and unsupported boundaries. This ABI evidence complements real system-sheet
captures; it does not substitute for them. New upstream matcher HEAD was inspected for WAL-1513;
its transaction extraction still uses floating point and two decimal places, so an upgrade alone does
not address this issue.

### Demo rollout dependency

Deploy the accompanying service and Docker issuer VCT catalogue updates before using the new SD-JWT
payment route against those demo services. They add English/German labels for `card_scheme`,
`card_last4` and `card_holder_name` to the authenticated attestation type document. Issuance metadata's
separate `credential_metadata.claims[].display[].name` is not a substitute for the VCT's authenticated
`claims[].display[].label`. A deployment still serving the previous VCT without these labels intentionally
has no SD-JWT system-payment candidate. Other issuers, including separately configured Enterprise
fixtures, must publish equivalent authenticated labels for their requested attributes.

A physical Android 12 signed-request check used a synthetic verifier whose public key was configured
independently in the isolated wallet. The actual wallet accepted the valid signature, audience and
origin, then displayed the exact amount and issuer labels with preview disabled. Confirmation reached
the native SCA policy rejection on the deliberately unprotected test identity. Protected identity setup
and signing require operator biometrics and remain an attended acceptance gate; this check is not a
successful protected SCA payment.

The attended physical `ScaPaymentE2ETest.sharesScaSdJwtWithNativeAuthorizationWithoutWalletReview`
prepares the remaining signed/protected acceptance path. On a fresh isolated **preview** install,
select it with `-e class id.walt.walletdemo.compose.android.ScaPaymentE2ETest#sharesScaSdJwtWithNativeAuthorizationWithoutWalletReview`
and `-e wallet.sca approve`. It creates the protected identity through the app, checks issuer labels
in the system sheet, then requires operator-approved signing and verifier acceptance with the exact
original transaction hash. It also requires the issuer-label deployment. Do not run it unattended.
