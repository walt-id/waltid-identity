# Vendored Credential Manager matcher

`src/androidMain/assets/id/walt/wallet2/mobile/identitycredentialmatcher.wasm` is the Credential
Manager matcher this wallet registers for its ISO 18013-7 Annex C (`org-iso-mdoc`) registry. AndroidX
ships no Annex C registry or matcher, so `AndroidDigitalCredentialRegistry` registers a raw
`DigitalCredentialRegistry` and supplies the binary itself. OpenID4VP is unaffected: it uses
`OpenId4VpRegistry` with the matcher AndroidX embeds.

## Why it is vendored

It was previously obtained by declaring `org.multipaz:multipaz-dcapi-android`, which is a dependency
on an artifact for one asset inside it. No Multipaz API is called, and the artifact's own runtime
dependencies were excluded, so its classes shipped to consumers with unresolvable references.
Vendoring the binary removes the dependency and keeps the exact bytes.

Enabling `androidResources` in this module's `build.gradle.kts` is required: Android KMP library
targets have asset and resource processing off by default, and without it `androidMain/assets` is not
packaged into the AAR at all. It is set through `androidComponents.finalizeDsl` because the `android`
script accessor does not exist when the module is configured with `enableAndroidBuild=false` for iOS.
The asset path is package-qualified so that it cannot collide with another library's copy during the
application-level asset merge.

## Provenance

| | |
| --- | --- |
| Upstream repository | https://github.com/openwallet-foundation/multipaz |
| Release tag | `0.100.0` (annotated tag object `dae28ac7f7df30650a39258fcce8be813c46d53b`) |
| Commit | `7c0988bee3384d13a0732e0c33336ae0faf3b863` |
| Upstream path | `multipaz-dcapi/src/androidMain/assets/identitycredentialmatcher.wasm` |
| Git blob | `ebbc2f2671623f803c46c1a18d5f76e690c7e385` |
| Size | 297,404 bytes |
| SHA-256 | `420385a46bf554b34224c960051a0cd6c4ecff12ca2c3bdb8948a555afd6e0f8` |

`NOTICE-identitycredentialmatcher.txt` ships beside the binary and records the licenses of the
components compiled into it: Multipaz's matcher sources and libcppbor (Apache-2.0), cJSON (MIT) and
base64 (0BSD). Multipaz publishes no `NOTICE` file at that commit. Because the matcher is no longer a
Maven dependency, the generated `THIRD-PARTY-NOTICE.md` reports no longer mention it — that notice
file is the record.

## Compatibility contract

The matcher's credential-database input and the identifiers it emits are Multipaz-private
conventions, not a specification, and this module depends on them in three ways:

- `AndroidDigitalCredentialRegistry.encodeAnnexCCredentialDatabase` writes the CBOR database the
  matcher reads.
- `AndroidDigitalCredentialProvider.parseMatcherCredentialId` parses the matcher's
  `"<combination-index> <protocol> <document-id>"` credential ids.
- The matcher exposes no `metadata` channel, so an Annex C selection carries no `dc_request_index`.
  `resolveSelectedProtocolRequest` therefore identifies the selected request by the protocol the
  credential ids name, and only when exactly one offered request uses it. The matcher's
  `"<combination-index> <protocol>"` set id is not parsed: `parseMatcherSelection` matches
  `credentialSetId` only against the AndroidX OpenID4VP `req:<n>;…` shape, which an Annex C set id
  does not satisfy.

## Refreshing

Refreshing is **not** a file swap. Replace the binary:

```shell
git -C <multipaz> cat-file -p <commit>:multipaz-dcapi/src/androidMain/assets/identitycredentialmatcher.wasm \
  > src/androidMain/assets/id/walt/wallet2/mobile/identitycredentialmatcher.wasm
```

then update the table above, the pinned hash in `AndroidVendoredMatcherTest`, and the notice file if
the bundled third-party set changed. Re-check every point in the contract above against the new
matcher sources: the database the new matcher expects, the credential-id shape it emits, and whether
it now supplies a `metadata` request index — gaining one would change how a selection is attributed,
not just what it is parsed with. Then re-run the Annex C and multi-protocol tests:
`AndroidDigitalCredentialProviderTest`, `AnnexCHpkeAndroidTest` and `DigitalCredentialSharingE2ETest`.

Because the matcher is vendored rather than declared as a dependency, ordinary dependency-update
tooling will not detect future Multipaz releases. Automating that detection is deliberately left to a
follow-up, and such updates must not auto-merge given the compatibility contract above.
