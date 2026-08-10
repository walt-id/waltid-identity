# Vendored OpenID4VCI creation matcher

`src/androidMain/assets/id/walt/wallet2/mobile/provision_hardcoded.wasm` is the Credential
Manager matcher this wallet registers for OpenID4VCI `CREATE_CREDENTIAL` creation options.
AndroidX does not yet ship an OpenID4VCI creation registry helper comparable to
`OpenId4VpRegistry`, so `AndroidDigitalCredentialRegistry` registers creation options with a
raw `RegisterCreationOptionsRequest` and supplies the binary itself.

## Why it is vendored

It is obtained from the CMWallet sample (`digitalcredentialsdev/CMWallet`) rather than declared
as a dependency. No CMWallet API is called; only the matcher bytes and the creation-options
database layout that matcher expects are reused.

## Provenance

| | |
| --- | --- |
| Upstream repository | https://github.com/digitalcredentialsdev/CMWallet |
| Upstream path | `app/src/main/assets/provision_hardcoded.wasm` |
| Matcher sources | `matcher-rs/` (`issuance` / provision matcher) |
| Git blob | `262eee146c3763fdad3419305dc91ba8b045fb0d` |
| Size | 56,376 bytes |
| SHA-256 | `d6b4846072839bb43b98dfa5da5ae9ec83f2c30ce875c1ebd19c5ad2b5344ac1` |

`NOTICE-provision_hardcoded.txt` ships beside the binary.

## Compatibility contract

- `AndroidDigitalCredentialRegistry.encodeOpenId4VciCreationOptions` writes the binary database
  the matcher reads: little-endian JSON offset, icon PNG bytes, then a JSON `display` object.
- Creation registry id is `openid4vci`.
- Supported create protocols accepted at the provider boundary are
  `openid4vci-v1`, `openid4vci1.0`, and `openid4vci`.

## Refreshing

```shell
curl -sL \
  https://raw.githubusercontent.com/digitalcredentialsdev/CMWallet/main/app/src/main/assets/provision_hardcoded.wasm \
  > src/androidMain/assets/id/walt/wallet2/mobile/provision_hardcoded.wasm
```

Then update the table above and the pinned hash in `AndroidVendoredMatcherTest`.
