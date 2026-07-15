# WAL-896 PR #1885 Review Findings and Corrected Implementation Logic

- Date: 2026-07-15
- PR: <https://github.com/walt-id/waltid-identity/pull/1885>
- Companion document: [WAL-896-FIX-PLAN.md](WAL-896-FIX-PLAN.md)

## Executive Summary

The initial review found that PR #1885 was not ready to merge. The existing fix plan recorded all 20 inline comments, but several fixes were partial or based on incomplete protocol rules.

The remediation described below has now been implemented locally. The main outcomes are:

- one Request Object resolution and validation path is used by presentation and inspection;
- Request Object common claims, Request URI POST, wallet metadata, and replay binding follow OID4VP Final;
- X.509 authentication terminates at wallet-controlled trust anchors, with typed failures and deployable server/mobile configuration;
- encryption selection occurs before VP generation and the same selected key binds both the mdoc transcript and JWE;
- Kotlin/JS JWE encryption/decryption is implemented instead of failing at runtime;
- Final DCQL paths, exact presentation audience, reject-all transaction-data defaults, and authoritative conformance results are restored;
- unrelated Issuer2 defaults and duplicate conformance code are removed/restored.

Merge readiness still depends on the official OIDF plans and the repository's Android/iOS and GitHub CI jobs passing. Those external gates were not available in this local environment.

## Sources Reviewed

- OpenID for Verifiable Presentations 1.0 Final:
  <https://openid.net/specs/openid-4-verifiable-presentations-1_0.html>
- OpenID4VC High Assurance Interoperability Profile 1.0 Final:
  <https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0-final.html>
- JWT-Secured Authorization Request (JAR), RFC 9101:
  <https://www.rfc-editor.org/rfc/rfc9101.html>
- PR metadata, all 20 inline comments, the CHANGES_REQUESTED review, and current check results.
- The PR diff at commit `5f6b184a7` and the uncommitted fixes in the working tree.

The GitHub CLI was not installed, so REST API data was used. This exposed all comments and reviews, but not review-thread resolution state.

## Implemented Remediation and Verification

Implementation details:

- `AuthorizationRequestResolver` handles by-value and by-reference Request Objects, rejects ambiguous parameter combinations and case-invalid methods, requires the JWT media type, and sends the Final wallet-metadata field names.
- `SignedRequestValidator` validates `typ`, discovery-specific `aud`, outer/inner `client_id`, and `wallet_nonce` before handling `alg=none`; authenticated prefixes cannot use `alg=none` even under a generic development policy.
- `X509TrustPolicy` and `WalletX509TrustConfig` provide explicit PEM/system trust anchors, revocation and HAIP policy flags, and allowed Request Object algorithms. Server wallet descriptors persist this configuration and mobile factories accept it.
- `x509_hash` and `x509_san_dns` retain distinct errors for malformed JWS, missing `x5c`, missing trust policy, untrusted chain, invalid signature, identifier mismatch, included trust anchor, and disallowed leaf anchor.
- response encryption requires unique `kid` values, explicit supported `alg`, EC P-256 public parameters, compatible `use`, and supported `enc`; key choice is stable across JWK ordering.
- the selected encryption context is created once, its RFC 7638 thumbprint is used in the mdoc handover, and its key/alg/enc/kid are reused for the final compact JWE.
- pre-decoded JSON presentation requests were removed from the wallet-facing API; the original URL is required so request authentication cannot be bypassed.
- obsolete duplicate conformance adapters/plans were deleted, and automated success now requires an actual suite `PASSED` result.

Successful local gates:

- DCQL JVM tests;
- Client Identifier Prefix JVM tests, including trusted SAN and typed X.509 policy failures;
- OpenID4VP wallet JVM and JS Node tests, including a production JWE encrypt/decrypt round trip and mdoc transcript vectors;
- OpenID4VP verifier JS compilation;
- wallet server and wallet API test compilation;
- conformance-runner test compilation;
- Issuer2 profile tests;
- `git diff --check` on the working tree.

Not locally executable:

- Android and iOS/mobile documentation builds, because both mobile targets are disabled in the local Gradle configuration and no Android SDK/Xcode environment is available;
- live OIDF conformance plans, because the external conformance services and wallet/issuer stack were not running;
- GitHub review-thread status and CI re-check, because the GitHub CLI/connector is unavailable in this environment.

## Corrections to the Existing Fix Plan

### Status accounting

The original plan reported 20 total issues, 3 fixed, and 17 remaining, while listing Fixes 1 through 4 as complete and Fixes 5 through 20 as remaining. That status was internally inconsistent. The remediation treated all four early fixes as incomplete and reworked them through the shared resolver and validator.

### Normative section references

Use the following Final-spec locations when implementing and reviewing:

| Behavior | Correct reference |
| --- | --- |
| Request Object `typ` and general Request Object processing | OID4VP Section 5 |
| Authorization Request parameters | OID4VP Section 5.1 |
| Request Object `aud` | OID4VP Section 5.8 |
| Client Identifier Prefixes, including `x509_hash` | OID4VP Section 5.9 |
| Request URI POST and wallet metadata | OID4VP Section 5.10 |
| DCQL Claims Query | OID4VP Section 6.3 |
| Format-specific claims paths | OID4VP Section 7 |
| Encrypted Authorization Responses | OID4VP Section 8.3 |
| `direct_post.jwt` | OID4VP Section 8.3.1 |
| mdoc OpenID4VP handover | OID4VP Appendix B.2.6.1 |
| Presentation audience and replay binding | OID4VP Section 14.1 and Appendix B.3.6 |

## Detailed Findings and Implementation Logic

### 1. Use one authenticated Authorization Request resolver

Affected code:

- `AuthorizationRequestResolver.kt`
- `WalletPresentFunctionality2.kt`
- `WalletPresentationHandler.kt`
- mobile/server request-inspection APIs

The current implementation has multiple independent resolution paths. `AuthorizationRequestResolver` invokes `SignedRequestValidator`, but `WalletPresentFunctionality2` still reimplements Request URI fetching and only invokes the validator for the `request_uri` JWT response. An inline `request=<JWT>` is not resolved by that path. Request inspection independently fetches and decodes untrusted data.

Implementation logic:

1. Make `AuthorizationRequestResolver` the only component that parses or fetches an Authorization Request.
2. Have both presentation and inspection call the same resolver and receive a typed result that records authentication status and resolved verifier metadata.
3. Support these inputs explicitly:
   - plain Authorization Request parameters;
   - `request` Request Object by value;
   - `request_uri` with absent method or `get`;
   - `request_uri` with `post` and wallet metadata.
4. Reject `request_uri_method` values other than the case-sensitive strings `get` and `post`.
5. Reject `request_uri_method` when `request_uri` is absent.
6. For `request_uri_method=post`, require an `application/oauth-authz-req+jwt` response as specified by Section 5.10.1. Do not accept a plain JSON response on this path.
7. Do not expose verifier identity, response URI, nonce, DCQL, or encryption information to consent UI until resolution and authentication have succeeded. If an unauthenticated preview is retained, its type and UI representation must explicitly state that it is unverified and it must not be mixed with authenticated data.
8. Remove duplicate fetch/parse code after all callers use the resolver.

### 2. Validate every Request Object before branching on its signature algorithm

Affected code:

- `SignedRequestValidator.kt`

The current validator returns success immediately for an allowed `alg=none` Request Object. This bypasses `typ`, `aud`, `client_id`, outer/inner equality, and `wallet_nonce` checks.

Implementation logic:

1. Parse the JOSE header and payload once.
2. Validate `typ == oauth-authz-req+jwt` for every Request Object, including an unsecured Request Object.
3. Require the Request Object `client_id` claim.
4. Require the outer Authorization Request `client_id` and compare it with the Request Object `client_id`. RFC 9101 Section 6.3 requires the values to be identical.
5. Require and validate `aud` according to the discovery mode:
   - Static Discovery: exactly `https://self-issued.me/v2`.
   - Dynamic Discovery: the Wallet issuer (`iss`) discovered by the Verifier.
6. Do not model Dynamic Discovery audience as an arbitrary wallet authorization endpoint. Pass an explicit discovery mode and expected issuer/audience into validation.
7. Validate the returned `wallet_nonce` whenever the Wallet sent one.
8. Determine from the Client Identifier Prefix and wallet policy whether a signature is required. HAIP always requires a signed request using `request_uri` and `x509_hash`.
9. Only after the common checks, either reject `alg=none` or authenticate the signed Request Object.
10. Return typed validation failures instead of relying on message parsing.

Required negative tests include missing/wrong `typ`, missing/wrong `aud`, missing outer `client_id`, mismatched client IDs, missing/mismatched `wallet_nonce`, disallowed `alg=none`, malformed JWT, and invalid signature.

### 3. Correct Request URI POST wallet metadata

Affected code:

- `AuthorizationRequestResolver.WalletCapabilities`
- `buildRequestUriPostWalletMetadata`
- the main wallet presentation fetch path

The wallet currently emits draft/wrong names and the main presentation path sends only `wallet_nonce`.

Implementation logic:

1. Emit `authorization_encryption_alg_values_supported` and `authorization_encryption_enc_values_supported` in `wallet_metadata`.
2. Keep these distinct from Verifier `client_metadata`, where `encrypted_response_enc_values_supported` is the correct parameter name.
3. Send a UTF-8 `application/x-www-form-urlencoded` body containing:
   - `wallet_metadata` when capabilities need to be conveyed;
   - `wallet_nonce` when replay binding is requested.
4. Send `Accept: application/oauth-authz-req+jwt`.
5. Add a transport-level test that inspects the exact POST method, headers, form fields, encoding, and response content type.

### 4. Implement an explicit X.509 trust policy

Affected code:

- `waltid-openid4vp-clientidprefix`
- `X509Hash.authenticateX509Hash`
- `X509SanDns.authenticateX509SanDns`
- request-validation configuration exposed by wallet applications

The current authenticators verify signatures within the requester-supplied chain, the Request Object signature, and the identifier binding. They do not establish trust in a wallet-configured root.

HAIP does not define a universal trust-anchor set. Trust anchors and applicable certificate policies are supplied by the ecosystem or wallet deployment.

Implementation logic:

1. Add an explicit trust-policy input to request authentication. It should contain configured trust anchors and policy options; do not read implicit global state inside the authenticator.
2. Decode `x5c` into leaf plus intermediates.
3. Validate time validity, chain signatures, issuer/subject linkage, basic constraints, path-length constraints, key usage, and applicable extended key usage/policy rules.
4. Require the constructed path to terminate at a configured trust anchor. Never trust a self-signed root merely because it appears in requester-controlled `x5c`.
5. Verify the Request Object with the validated leaf public key.
6. Apply the scheme-specific binding:
   - `x509_hash`: compare the base64url SHA-256 hash of the DER leaf certificate with the Client Identifier value.
   - `x509_san_dns`: match the Client Identifier DNS name against the leaf SAN according to the required DNS matching rules.
7. For HAIP:
   - require `x509_hash`;
   - reject an `x5c` that contains the trust anchor;
   - reject a self-signed signing certificate;
   - require P-256/ES256 support and any ecosystem certificate profile rules.
8. Do not collapse all failures into `X509HashMismatch`; retain typed failures for untrusted root, expired certificate, invalid chain, identifier mismatch, and invalid Request Object signature.

Tests must use at least a trusted chain, an untrusted root, a requester-included root, a self-signed leaf, an expired leaf, a broken intermediate signature, a wrong hash/SAN, and a valid chain with an invalid Request Object signature.

### 5. Select and validate response encryption before generating a VP

Affected code:

- `ResponseEncryptionHandler.kt`
- `WalletPresentFunctionality2.kt`
- `MdocPresenter.kt`
- JWE support in `JWKKey` platform implementations

The current code chooses the first `use=enc` key or the first key, defaults the JWK `alg`, and does not select encryption until after VP generation. This makes encrypted mdoc device authentication use the wrong transcript.

Implementation logic:

1. Define the wallet's supported combinations by platform, for example HAIP's required `EC`/`P-256`/`ECDH-ES` and supported `A128GCM`/`A256GCM` content encryption.
2. Parse all JWKs from authenticated Verifier metadata.
3. Require each client-metadata JWK to contain a unique `kid` and an `alg`.
4. A key is eligible only when:
   - `alg` is supported;
   - `kty`, `crv`, and other parameters are valid for that algorithm;
   - `use`, when present, is compatible with encryption;
   - the key contains the required public parameters;
   - the platform implementation can actually encrypt with it.
5. Select one eligible key using explicit wallet preference. Fail if no key is eligible; never fall back to an arbitrary key.
6. Ensure the produced JWE `alg` exactly equals the selected JWK `alg`.
7. Select `enc` from the authenticated `encrypted_response_enc_values_supported` list and the wallet-supported set. Use `A128GCM` only when the parameter is absent, as permitted by OID4VP.
8. Include the selected JWK `kid` in the protected JWE header.
9. Retain a single immutable encryption context containing the selected raw JWK, imported public key, `kid`, `alg`, `enc`, and RFC 7638 thumbprint.
10. Create this context before generating `vp_token`.
11. Pass its thumbprint into mdoc session transcript generation. Reuse the same context to encrypt the final response; do not select the key a second time.
12. Ensure the JWE plaintext has `vp_token`, optional `id_token`, and optional `state` as top-level members.
13. POST exactly one `response=<compact-JWE>` field using `application/x-www-form-urlencoded` for `direct_post.jwt`.

Platform note: JS `JWKKey.encryptJwe` is currently unimplemented. Either implement and test it, or explicitly exclude/declare unsupported encrypted response behavior on JS rather than claiming cross-platform support.

### 6. Build the correct encrypted mdoc transcript

Affected code:

- `MdocPresenter.buildSessionTranscript`
- VP generation orchestration

For an encrypted response, the third `OpenID4VPHandoverInfo` element must be the RFC 7638 SHA-256 thumbprint of the exact Verifier public key used for response encryption, encoded as required by Appendix B.2.6.1. It is `null` only for an unencrypted response.

Implementation logic:

1. Change mdoc presentation input to accept the already selected encryption context or thumbprint.
2. Use the thumbprint for `direct_post.jwt` and other encrypted modes.
3. Use `null` for an unencrypted response.
4. Preserve the correct fourth element for the response mode (`response_uri`, `redirect_uri`, or DC API origin as applicable).
5. Verify the resulting Device Authentication using the verifier's production transcript reconstruction, not only by inspecting CBOR fields.

### 7. Restore Final DCQL Claims Query semantics

Affected code:

- `ClaimsQuery.kt`
- `DcqlMatcher.kt`
- `DcSdJwtPresentation.kt`
- `MdocPresenter.kt`
- tests and conformance configuration

Implementation logic:

1. Make `path` required and non-empty in `ClaimsQuery`.
2. Remove `namespace`, `claim_name`, `effectivePath()`, and `pathKey()` compatibility behavior introduced by this PR.
3. Update every consumer to use `path` directly.
4. For `mso_mdoc`, require exactly two path elements and require both to be JSON strings:
   - element 0: namespace;
   - element 1: data element identifier.
5. Do not accept deeper paths for mdoc; the Final format rule requires exactly two strings.
6. Add serialization/precheck tests rejecting missing, empty, non-string, one-element, and three-element mdoc paths.

### 8. Remove the non-compliant alternate presentation audience

Affected code:

- `AudienceCheckSdJwtVPPolicy.kt`
- `PresentationVerificationEngine.kt`
- `VPVerificationContext.kt`
- `X509HashUtils`
- `OSSVerifier2Manager.kt`

The PR permits a presentation audience derived from the request certificate even when it differs from the Authorization Request Client Identifier. OID4VP requires the presentation to bind to the exact Client Identifier, except for DC API where the prefixed origin is used.

Implementation logic:

1. Remove the `x509HashAudience` alternative from presentation verification.
2. Validate KB-JWT `aud` against exactly:
   - the Authorization Request `client_id` for normal OID4VP;
   - `origin:<origin>` for DC API.
3. Do not infer the Client Identifier Prefix solely from the presence of `x5c`.
4. Require an explicit Client Identifier or explicit scheme configuration when creating a verifier session.
5. If automatic `x509_hash` generation remains as a convenience, make it an explicit `x509_hash` configuration option and set the generated value as the actual Authorization Request `client_id`. Then normal exact-audience verification is sufficient.

### 9. Fix KMP compilation without expect/actual duplication

Affected code:

- `X509HashUtils.kt`
- `X509HashUtils.jvm.kt`

Implementation logic:

1. Prefer a common implementation using existing Kotlin Multiplatform SHA-256 and base64url utilities.
2. Remove the JVM-only `expect`/`actual` pair if no platform-specific API is required.
3. Test the same certificate vector on JVM, JS, Android, and iOS targets configured by the module.
4. Avoid returning `null` for every error where callers need to distinguish invalid certificate input from an absent configuration.

### 10. Correct mobile public API visibility and authentication semantics

Affected code:

- `MobileWallet.kt`

Implementation logic:

1. Decide whether the new inspection types are part of the public SDK.
2. If public, add explicit `public` visibility to each declaration and property required by explicit API mode.
3. If not public API, mark them `internal` and do not expose them from public functions.
4. Return only authenticated inspection results as described in Finding 1.
5. Compile the Android and iOS source sets in CI after the change.

### 11. Revert global Issuer2 signing defaults

Affected code:

- `waltid-services/waltid-issuer-api2/config/issuer2-profiles.conf`

Implementation logic:

1. Restore the original `defaultIssuerKey`, `defaultIssuerDid`, and default certificate behavior.
2. Add a dedicated conformance-only profile containing the conformance key and leaf/intermediate chain.
3. Do not place the trust anchor in an HAIP `x5c` chain.
4. Ensure the signing private key matches the leaf certificate public key.
5. If a DID is used, derive it from the same public key and add a configuration test asserting the match.
6. Do not make short-lived conformance material the default for unrelated profiles.

### 12. Do not invent supported transaction-data types

Affected code:

- `TransactionDataTypeRegistry.kt`
- the default `transactionDataTypeRegistry` in wallet presentation

The PR calls `payment_confirmation` and `qes_authorization` standard OID4VP/HAIP types, but neither specification defines those concrete types. A supported type also requires complete validation of its type-specific structure, not only recognition of its name.

Implementation logic:

1. Default to a reject-all registry when no transaction-data profiles are configured.
2. Replace a set of names with registered profile validators that validate allowed fields, required fields, types, values, credential bindings, and presentation output requirements.
3. Register payment/QES types only from the external specifications or ecosystem profiles that define them.
4. Reject known type names whose bodies fail their profile validation.
5. Keep a permissive mode out of HAIP and production defaults.

### 13. Make conformance results authoritative

Affected code:

- `WalletTestPlanRunner.kt`
- `VpWalletConformanceTests.kt`
- `WalletConformanceTests.kt`
- wallet adapters and plan classes

Implementation logic:

1. Remove all generic or specific error-string classification from conformance pass/fail logic.
2. Always poll the suite to a terminal state with a bounded timeout and retrieve its actual final result.
3. Automated success requires the suite result `PASSED`.
4. Treat `FAILED`, `ERROR`, `INTERRUPTED`, `UNKNOWN`, and timeout as test failures.
5. Treat `REVIEW` as manual/inconclusive, never as an automated pass. If the suite requires manual evidence, separate those modules from automated certification claims.
6. Do not automatically convert `WARNING` to `PASSED`; define an explicit policy or fail it.
7. Make every JUnit test assert the complete returned result set. Combined runners must aggregate and assert all plans.
8. Test expected local rejection behavior separately with typed unit/integration assertions. A local exception does not prove that the conformance module passed.
9. Prefer one wallet adapter and one set of test plans. The newly added `WalletConformanceAdapter`, `WalletConformanceTests`, and `WalletPlan1/2/7` duplicate the existing VP wallet path and use obsolete protocol fields. Delete them unless a distinct, valid purpose is demonstrated.
10. Ensure the retained adapter forwards a complete Authorization Request without rewriting protocol semantics or fabricating response parameters.

## Test Design Required Before Merge

### Request Object matrix

- request by value and by reference;
- Request URI GET and POST;
- exact method case validation;
- valid and invalid `typ`;
- static and dynamic `aud` cases;
- missing/mismatched outer and inner `client_id`;
- valid and invalid `wallet_nonce`;
- valid signature, invalid signature, unsecured allowed by explicit non-HAIP policy, and unsecured rejected;
- every supported Client Identifier Prefix;
- trusted and untrusted X.509 chains.

### Encryption matrix

- multiple JWKs with deterministic supported-key selection;
- missing `alg` and missing/duplicate `kid` rejection;
- conflicting `use`, unsupported `kty`, unsupported `crv`, and unsupported `alg` rejection;
- default `A128GCM` and negotiated `A128GCM`/`A256GCM`;
- protected header assertions for `alg`, `enc`, and `kid`;
- plaintext top-level `vp_token`, optional `id_token`, and `state`;
- exact `application/x-www-form-urlencoded` `response` transport;
- wallet encryption followed by production verifier decryption.

### mdoc matrix

- unencrypted transcript with null key thumbprint;
- encrypted transcript with the selected key's RFC 7638 thumbprint;
- a different encryption key must fail Device Authentication verification;
- production verifier round trip for both paths.

### Build and hygiene gates

- JVM, JS, Android, and iOS compilation for every affected KMP module;
- unit and integration tests for affected modules;
- actual OIDF conformance result assertions;
- `git diff --check` with no errors;
- no unrelated configuration or generated artifacts;
- all required GitHub checks green.

## Recommended Change Sequence

### Phase 0: Reduce risk and scope

1. Revert global Issuer2 defaults.
2. Remove duplicate/obsolete conformance adapter and test-plan code.
3. Remove unrelated changes that do not support WAL-896.
4. Clean trailing whitespace and stale compliance claims in documentation.

### Phase 1: Establish trusted request input

1. Consolidate resolution into `AuthorizationRequestResolver`.
2. Correct common Request Object validation order and discovery-aware audience handling.
3. Implement exact Request URI POST behavior and authenticated inspection.
4. Add explicit X.509 trust-policy plumbing and tests.

### Phase 2: Restore protocol data models

1. Restore Final DCQL Claims Query semantics.
2. Remove alternate/fabricated presentation audience behavior.
3. Replace transaction type names with actual registered validators.

### Phase 3: Implement response encryption correctly

1. Introduce a single immutable selected-encryption context.
2. Validate algorithms and keys against platform support.
3. Select before VP generation.
4. Pass the selected key thumbprint into mdoc transcript generation.
5. Reuse the same context for JWE creation and transport.

### Phase 4: Prove behavior

1. Add request, trust, JWE, transport, and mdoc round-trip tests.
2. Repair conformance result handling and assertions.
3. Run the complete supported-platform build matrix.
4. Run official conformance plans and report only the suite's actual results.

## Merge Acceptance Criteria

The PR should not be approved until all of the following are true:

- every actionable review thread is addressed by code, tests, or an agreed documented response;
- the request resolver and inspection APIs share one authenticated path;
- X.509 request authentication terminates at wallet-configured trust anchors;
- presentation audience equals the exact Client Identifier, except for the defined DC API origin case;
- encrypted mdoc Device Authentication verifies end to end;
- no arbitrary JWK or algorithm fallback remains;
- no draft DCQL fields or `presentation_submission` remain in the OID4VP 1.0 path;
- conformance tests cannot fabricate success;
- global Issuer2 defaults are restored;
- all affected platform compilations and required CI checks pass;
- `git diff --check` is clean;
- documentation reports only verified, reproducible conformance results.
