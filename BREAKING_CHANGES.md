# Breaking changes since 0.3.1

## waltid-dcql identifier charset

- `DcqlQuery.precheck()` now rejects credential and claim identifiers outside OpenID4VP §6.1 / §6.3 (`A-Za-z0-9_-`).
- Claim `id` remains optional. When present, dotted or colon-separated values such as `urn:eudi:pid:1` or `address.street_address` fail precheck.
- Callers that previously reused issuer configuration ids as DCQL query ids must sanitize them first.

## waltid-dcql JVM ABI (1.0.x)

- `DcqlDisclosure` gained optional `location: List<JsonElement>? = null` so SD-JWT claim paths can be carried through matching and holder-policy checks.
- Kotlin source that constructed or copied `DcqlDisclosure` with two arguments remains valid via the default parameter.
- JVM binary consumers compiled against published `waltid-dcql-jvm` 1.0.0 must rebuild against this identity head. The descriptors for `<init>` and `copy` change from `(String, JsonElement)` to three-argument forms. There is no `@JvmOverloads` / synthetic two-arg compatibility shim.

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
