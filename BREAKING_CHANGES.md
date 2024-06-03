# Breaking changes since 0.3.1

## Configuration:

#### Database
- Unified db.sqlite.conf, db.postgres.conf, db.mssql.conf, db.conf (referencing either of the first 3) to just db.conf
  - remove your db.conf (which only contains the link to any of the other config files)
  - rename your db.sqlite.conf/db.postgres.conf/db.mssql.conf (whichever you actively use) to db.conf
  - rename `hikariDataSource` to `dataSource`

#### OCI integration
- Moved functionality of individual oci.conf / oci-rest-api.conf to key-generation-defaults.conf
- TODO: Test this

#### OIDC
- Moved `publicBaseUrl` from web.conf to oidc.conf -> now only required to be set when OIDC login is used
