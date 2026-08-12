# Vendored OpenID4VP Credential Manager matcher

`src/androidMain/assets/id/walt/wallet2/mobile/openid4vpmatcher.wasm` is the Credential Manager
matcher this wallet registers for its OpenID4VP registry, in place of the one AndroidX embeds in
`OpenId4VpRegistry`. Only the matcher binary is ours: `AndroidDigitalCredentialRegistry` still builds
an `OpenId4VpRegistry` and re-registers its `credentials` bytes verbatim, so AndroidX remains the sole
serializer of the registry format.

This is separate from the Annex C matcher described in `ANNEX-C-MATCHER.md`. The two registries are
registered independently and share no code beyond both being raw `DigitalCredentialRegistry`
subclasses.

## Why it is vendored

AndroidX's embedded matcher cannot report a transaction data prompt and a second credential from the
same request. Given a valid OpenID4VP request for two DCQL credentials with one `transaction_data`
entry bound to one of them, it reports zero candidates and the picker shows "Your info wasn't found".

The mechanism, from the matcher's own WebAssembly: once `transaction_data` is present,
`report_matched_credential` takes a payment-only emit path whose sole exits are
`AddPaymentEntryToSetV2` / `AddPaymentEntryToSet` / `AddPaymentEntry`. That path is entered per
credential and compares the candidate against `credential_ids` using `i32.load offset=8`, the cJSON
`child` pointer — element 0 only, with no sibling walk. A candidate that is not that first id skips
every `Add*` call and emits nothing at all; the ordinary `AddStringIdEntry` / `AddEntryToSet` path
lives in a sibling loop reached only when no transaction data is present, so there is no fallthrough.
Meanwhile the option was already declared to the platform with `AddEntrySet(setId, 2)`. Declared two,
delivered one, so the platform discards the whole option — which is why the failure is total rather
than degrading to the payment credential alone.

So the failure is specific to the transaction data reporting path, not to generic multi-credential DCQL
matching.

The Rust matcher decides per credential instead: it iterates all of `credential_ids`, reports a match
as a payment entry, and reports every other credential in the option as a standard verification entry
via an explicit `if !reported` fallback.

Not fixable by upgrading: `1.0.0-alpha05` is the newest published `registry-digitalcredentials-openid`
and `androidx-main` HEAD embeds a byte-identical matcher (SHA-256
`b2f7eb86acd339f5634506d76d9efea345d80b6799e5d42d954b1b9af709670e`, clang 16). Nor by substituting
the Annex C matcher we already vendor, which implements no `transaction_data` at all.

## Provenance

The committed binary is **our build of unmodified upstream sources**, not an artifact Google publishes:
upstream ships the Rust sources only.

| | |
| --- | --- |
| Upstream repository | https://github.com/android/identity-samples |
| Branch | `wasm` |
| Commit | `5d966fc4913cac93f3b3b11e11bdd44d3e0b5c9e` |
| Upstream path | `CredentialProvider/wasm/matcher-rs` |
| Source modifications | none |
| Built artifact | `target/wasm32-unknown-unknown/release/presentation.wasm`, built here |
| Size | 117,546 bytes |
| SHA-256 | `5f1738caf65854d8999701dd54a7caafbd8c2fccc355be340e553fe16f5cfd79` |
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
git -C identity-samples checkout <pinned-commit>
cd identity-samples/CredentialProvider/wasm/matcher-rs
rustup toolchain install nightly --component rust-src --target wasm32-unknown-unknown
bash build.sh
cp target/wasm32-unknown-unknown/release/presentation.wasm \
  <this-module>/src/androidMain/assets/id/walt/wallet2/mobile/openid4vpmatcher.wasm
```

So the maintenance model is:

- **The committed binary** is verified by the SHA-256 that `AndroidVendoredMatcherTest` pins. Nothing
  else needs to hold for it to keep working, and an accidental swap cannot go unnoticed.
- **An intentional update** picks an upstream commit, builds it with a toolchain recorded here, runs
  upstream's own test suite at that commit, runs the Android matcher regressions in
  `DigitalCredentialSharingE2ETest`, and then updates the asset, the pinned hash and this table
  together. Updating the binary without repinning the hash fails the test by design.

## Compatibility contract

Unlike the Annex C matcher, this one consumes the *AndroidX* registry format and emits the AndroidX
`metadata` channel, so `parseMatcherSelection`'s existing `req:<n>;…` handling applies unchanged and no
Multipaz-private convention is involved. Three things identified during this review still have to hold.
No full behavioural parity audit against AndroidX's matcher was performed, so this is not a closed list:

- **Protocol scoping.** The matcher iterates `registry.supported_protocols` and skips any request
  whose protocol is not listed, so registering only `openid4vp-v1-unsigned` keeps the wallet out of
  signed and multisigned requests. This is load-bearing: the matcher recognises all three
  `openid4vp-v1-*` protocols, and would serve signed requests the wallet cannot fulfill if the
  registry ever advertised them. `DigitalCredentialSharingE2ETest.doesNotSurfaceForSignedOrMultisignedRequests`
  asserts it against the real platform.
- **Host imports.** The matcher imports its host functions from module `credman`, whereas AndroidX's C
  matcher imports the set-based ones from `credman_v2` and `credman_v5`. Google Play services resolves
  both, but a future GMS that stops aliasing `credman` for those would break this binary and not
  AndroidX's.
- **Optional credential sets are dropped.** One known behaviour difference:
  `evaluate_explicit_credential_sets` filters on `required.unwrap_or(true)` and never evaluates
  `required: false` sets, so a credential only an optional set asks for contributes no candidates.
  AndroidX's C matcher reports them. Nothing in this repo sends optional sets, and no test covers them;
  required sets are covered by
  `DigitalCredentialSharingE2ETest.sharesTheSatisfiableCredentialSetOption`. Check this before sending
  an optional set to an Android holder.

## Removing it

Delete the asset, the notice, `AndroidOpenId4VpRegistry`, and its branch in
`AndroidDigitalCredentialRegistry.replace`, as soon as a released
`androidx.credentials.registry:registry-digitalcredentials-openid` embeds a matcher that reports a
payment entry and a standard entry from one request. Test for that with
`DigitalCredentialSharingE2ETest.sharesMdocWithScaPaymentTransactionDataAndSecondCredential`: drop the
custom registry and see whether it still passes.

Because the matcher is vendored rather than declared as a dependency, ordinary dependency-update
tooling will not detect upstream changes to it.
