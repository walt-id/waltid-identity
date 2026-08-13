# Vendored OpenID4VCI creation matcher

`src/androidMain/assets/id/walt/wallet2/mobile/provision_hardcoded.wasm` is the Credential
Manager matcher this wallet registers for OpenID4VCI `CREATE_CREDENTIAL` creation options.
AndroidX does not yet ship an OpenID4VCI creation registry helper comparable to
`OpenId4VpRegistry`, so `AndroidDigitalCredentialRegistry.registerCreationOptions` registers
creation options with a raw `RegisterCreationOptionsRequest` and supplies the binary itself.

## Why it is vendored

It is obtained from the CMWallet sample (`digitalcredentialsdev/CMWallet`) rather than declared
as a dependency. No CMWallet API is called; only the matcher bytes and the creation-options
database layout that matcher expects are reused.

## Provenance

| | |
| --- | --- |
| Upstream repository | https://github.com/digitalcredentialsdev/CMWallet |
| Upstream commit | `6b350ff8cfc9ed49b301603c25eb56fcd2a904b1` |
| Upstream asset path | `app/src/main/assets/provision_hardcoded.wasm` |
| Upstream git blob | `262eee146c3763fdad3419305dc91ba8b045fb0d` |
| Matcher sources | `matcher/issuance/provision.c` (C provision matcher; **not** `matcher-rs/`) |
| Supporting sources | `matcher/credentialmanager.h`, `matcher/cJSON/` |
| Size | 56,376 bytes |
| SHA-256 | `d6b4846072839bb43b98dfa5da5ae9ec83f2c30ce875c1ebd19c5ad2b5344ac1` |

`NOTICE-provision_hardcoded.txt` ships beside the binary.

### Build lineage

CMWallet builds the issuance matcher from `matcher/issuance/provision.c` into the asset named
`provision_hardcoded.wasm` under `app/src/main/assets/`. The exact CI/toolchain command may change
upstream; when refreshing, prefer copying the asset produced by that pinned commit rather than
rebuilding against a moving `main`.

## Licensing

As of the pinned commit above, the CMWallet repository does **not** publish a root `LICENSE`.
Redistribution of this compiled WASM is therefore a **release gate**: before publishing artifacts
that ship the binary, obtain an explicit upstream license covering the matcher / binary, an
explicit redistribution grant, or rebuild/replace it from clearly licensed sources.

The C matcher tree vendors cJSON (MIT). A provenance NOTICE alone does not grant redistribution
rights for the WASM.

## Compatibility contract

- `AndroidDigitalCredentialRegistry.encodeOpenId4VciCreationOptions` writes the binary database
  the matcher reads: little-endian JSON offset, icon PNG bytes, then a JSON `display` object.
- Creation registry id is `openid4vci`.
- The Android create provider accepts only the canonical Digital Credentials issuance protocol
  `openid4vci-v1`. The upstream C matcher also recognizes the historical alias `openid4vci1.0`
  for Credential Manager matching; that alias is not part of this SDK's provider API.

## Refreshing

Pin an immutable commit (do not refresh from mutable `main`):

```shell
COMMIT=6b350ff8cfc9ed49b301603c25eb56fcd2a904b1
curl -sL \
  "https://raw.githubusercontent.com/digitalcredentialsdev/CMWallet/${COMMIT}/app/src/main/assets/provision_hardcoded.wasm" \
  > src/androidMain/assets/id/walt/wallet2/mobile/provision_hardcoded.wasm
shasum -a 256 src/androidMain/assets/id/walt/wallet2/mobile/provision_hardcoded.wasm
```

Then update the table above, `NOTICE-provision_hardcoded.txt`, and the pinned hash in
`AndroidVendoredMatcherTest`.
