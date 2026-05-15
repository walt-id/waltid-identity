# Core-lib hardening batch

This file records exactly what was implemented in the current core `waltid-x509` hardening batch.

## Implemented

- Hardened the built-in known profile definitions with explicit definition validation via `X509CertificateProfile.validateDefinition()`.
- Added pragmatic profile/build-data validation via `X509CertificateBuildData.checkCompatibility(profile)`.
- Added issuance-spec validation via `X509CertificateIssuanceSpec.checkCompatibility(...)`.
- Added issued-certificate validation via `X509IssuedCertificateData.checkCompatibility(profile)`.
- Enforced the new issuance compatibility checks in `X509ProfileDrivenIssuer` before issuing and after constructing certificate data.
- Strengthened CSR compatibility checks for the supported profile set.
- Added `X509CertificateSigningRequestBuilder.applyProfile(...)` to prefill profile-managed CSR extensions.
- Added stricter known-profile validation rules for:
  - `iso.iaca`
  - `iso.document-signer`
  - `generic-ca`
  - `generic-end-entity`
  - `etsi.qwac`
  - `etsi.qsealc`
  - `etsi.psd2.transport`

## Practical checks now enforced

- Profile definition consistency:
  - CA profiles must carry CA-style `basicConstraints` and `keyCertSign`.
  - End-entity profiles must not carry CA-only key usages.
  - Known built-in profiles must match their expected key-usage / EKU / validity-policy shapes.
- Issuance input compatibility:
  - self-signed vs issuer-signed profile shape is checked up front
  - issuer-signed validity must stay within issuer validity
  - `generic-end-entity` must be issued by `generic-ca`
  - `iso.document-signer` must be issued by `iso.iaca`
  - ISO subject field shape is checked more strictly
  - ISO IACA issuer alternative name shape is checked more strictly
  - ISO document signer CRL URI is required and issuer alternative names in build data are rejected
  - `etsi.qwac` and `etsi.psd2.transport` require at least one DNS or IP SAN in compatibility checks
- CSR compatibility:
  - known profile subject shape is checked
  - disallowed SANs for ISO profiles are rejected
  - when CSR key usage / EKU extensions are present, they must be profile-complete for the known profile

## Tests added or updated

- Added common tests for profile definition validation.
- Added common tests for stricter build-data compatibility checks.
- Added common tests for stricter CSR compatibility behavior.
- Added JVM tests that verify the issuer rejects incompatible profile inputs.
- Added JVM assertions that issued certificate data validates against the selected profile.

## Documentation updated

- Updated `README.md` with generation + validation examples for:
  - generic CA / generic end-entity
  - ISO IACA
  - ISO document signer
  - profile-aware CSR generation for QWAC and ISO document signer
- Documented what is fully implemented versus intentionally out of scope.

## Intentionally not implemented in this batch

- No Enterprise work.
- No JVM issuance support yet for `etsi.qwac`, `etsi.qsealc`, or `etsi.psd2.transport`.
- No giant policy engine or arbitrary compliance framework.
- No claim of broader ETSI/CABF/PSD2 compliance beyond the checks that now exist in code and tests.
