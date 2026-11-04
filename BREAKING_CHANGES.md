# Breaking changes since 0.3.1

## Issuer keys from `x5c`

- Verification policies 2: `signature` fails for a credential whose issuer key is taken from its `x5c` certificate chain, unless `x509-trust` or `etsi-trust-list` runs for the same credential or a linked trust registry trusts that chain. Add `x509-trust` with your trust roots to the credential policies; for an `https` issuer, its signing certificate must name the issuer in a SAN (see `CHANGELOG.md`). mdoc is unchanged.
- Verifier2 sessions created without `vc_policies` default to `signature` alone, so they now reject such credentials.
- Code that runs `signature` outside the Verifier2 pipeline must set `PolicyServiceKey.CREDENTIAL_POLICY_IDS` or `TRUST_REGISTRY_RESOLVER` in the `PolicyExecutionContext`.
- Legacy policies: `signature` and `signature_sd-jwt-vc` require the chain to be anchored by `trustedRootCAs` (`TRUSTED_ROOT_CAS_CONTEXT_KEY`) in the policy context. The legacy verifier API fills it from the session's `trusted_root_cas`. Callers that pass no roots reject such keys: `Verifier.verifyJws(jwt)`, `OpenID4VCI.validateAuthorizationRequestToken`, and the issuer API's VP-token authorization response.
- `JwsSignatureScheme.verifyCrypto2(data, allowedAlgorithms, resolver)` and the deprecated `verify(data)` reject a key from `x5c`. Use `verifyCrypto2(data, trustRoots, ...)`.

## waltid-dcql identifier charset

- `DcqlQuery.precheck()` now rejects credential and claim identifiers outside OpenID4VP §6.1 / §6.3 (`A-Za-z0-9_-`).
- Claim `id` remains optional. When present, dotted or colon-separated values such as `urn:eudi:pid:1` or `address.street_address` fail precheck.
- Callers that previously reused issuer configuration ids as DCQL query ids must sanitize them first.
- ISO 18013-7 Annex C is unchanged: it still uses `requestedElements`, and the internally generated matching query is not run through OpenID4VP `precheck()`.

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
