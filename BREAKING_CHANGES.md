# Breaking changes since 0.3.1

## waltid-dcql identifier charset

- `DcqlQuery.precheck()` now rejects credential and claim identifiers outside OpenID4VP §6.1 / §6.3 (`A-Za-z0-9_-`).
- Claim `id` remains optional. When present, dotted or colon-separated values such as `urn:eudi:pid:1` or `address.street_address` fail precheck.
- Callers that previously reused issuer configuration ids as DCQL query ids must sanitize them first.
- ISO 18013-7 Annex C is unchanged: it still uses `requestedElements`, and the internally generated matching query is not run through OpenID4VP `precheck()`.

## waltid-dcql JVM ABI (1.0.x)

- `DcqlDisclosure` gained optional `location: List<JsonElement>? = null` so SD-JWT claim paths can be carried through matching and holder-policy checks.
- Kotlin source that constructed or copied `DcqlDisclosure` with two arguments remains valid via the default parameter.
- JVM binary consumers compiled against published `waltid-dcql-jvm` 1.0.0 must rebuild against this identity head. The descriptors for `<init>` and `copy` change from `(String, JsonElement)` to three-argument forms. There is no `@JvmOverloads` / synthetic two-arg compatibility shim.

## waltid-dcql SD-JWT claim selection

- SD-JWT claims paths are processed per OpenID4VP §7.1.1. A path selects only the claim it ends on, never its selectively disclosable children: `["nationalities"]` releases the array without its element disclosures; use `["nationalities", null]` or an index for the elements.
- `null` selects every array element instead of the first match. A path component that does not fit the selected element (for example a key on a string) fails the claim, so the credential no longer matches.
- `DcqlMatchResult.disclosuresByPath` lists, per claims path, the disclosures to present: those of the selected claims plus the disclosures containing them (RFC 9901 §4.2.6). Presenters now attach these by location instead of looking disclosures up by claim name.
- JVM ABI: `DcqlMatchResult` gained `disclosuresByPath`. Kotlin source stays valid through the defaults; JVM binary consumers must rebuild, as the `<init>` and `copy` descriptors change.
- Wallet2 presentation preview: rows for array elements now carry their array's claim name in `name` (for example `nationalities`) instead of `null`.
- Wallet2 presentation preview: rows for requested clear-text claims now carry the plain claim name in `name` (for example `address`, previously `"address"` with quotes, or the index for array elements), and their `value` leaves out selectively disclosable children that are not presented, matching what the verifier receives.

## Configuration:

#### Feature system
- Introduced feature system, which is controlled by `_features.conf`.

#### Database (waltid-wallet-api)
- Unified `db.sqlite.conf`, `db.postgres.conf`, `db.mssql.conf`, `db.conf` (referencing either of the first 3) to just `db.conf`
  - remove your `db.conf` (which only contains the link to any of the other config files)
  - rename your `db.sqlite.conf`/`db.postgres.conf`/`db.mssql.conf` (whichever you actively use) to `db.conf`
  - rename `hikariDataSource` to `dataSource`

#### OCI integration (waltid-wallet-api)
- wallet-api: Moved functionality of individual `oci.conf` / `oci-rest-api.conf` to `key-generation-defaults.conf`
- TODO: Test this

#### OIDC (waltid-wallet-api)
- Moved `publicBaseUrl` from `web.conf` to `oidc.conf` -> now only required to be set when OIDC login is used

#### Credential issuance metadata (waltid-issuer-api)
- Massively simplified `credential-issuer-metadata.conf` configuration structure for `supportedCredentialTypes`

#### (Non-breaking) No longer needed configurations (waltid-wallet-api)
- You can remove the no longer needed files:
  - `wallet.conf` (was remote-wallet-configuration)
  - `marketplace.conf`
  - `chainexplorer.conf`
- You can remove the no longer needed attributes:
  - `enableOidcLogin` from `oidc.conf` (now handled as feature switch)
