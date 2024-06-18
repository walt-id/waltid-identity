# Breaking changes since 0.3.1

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
