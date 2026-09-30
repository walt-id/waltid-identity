# JWT key-attestation implementation plan

Status: implementation added, 2026-09-28. This document records the agreed scope. See [configuration and migration notes](../key-attestation.md) for the implemented API.

The first feature is issuer-side support for `proofs.jwt` with a `key_attestation` JOSE header, including attestations covering one or several keys. Deliver the reusable library capability first, then configure it in issuer implementations. Keep one `CredentialProofVerifier` entry point and use existing crypto2, JOSE, JWK, and X.509 utilities.

The library will verify evidence and enforce protocol requirements. Hosts will supply accepted attesters, issuer-specific trust material, resource limits, and optional deployment policy. Client-attestation trust remains independently configured. Wallet/provider APIs for acquiring attestations are outside this feature. Standalone `proofs.attestation`, `di_vp`, Federation trust-chain processing, and adding outer-proof `x5c` resolution are later capabilities; an attester's inner `x5c` chain is in scope.

The specification basis is [Appendix D.1](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0-final.html#appendix-D.1) and [Appendix F.1](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0-final.html#appendix-F.1): the outer JWT has one signing key; the attestation supplies a nonempty public-key list; the signer must belong to that list. The nested attestation requires `exp` and applicable nonce validation. Issuance per attested key is recommended, while the issuer can return fewer credentials. [Metadata](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0-final.html#section-12.2.4) limits the proof array through `batch_size`; it does not define that field as a limit on attested keys. Attestation acquisition and selection of trusted authorities depend on the deployment.

The milestones below are ordered implementation units. Existing tracked and untracked work in the workspace must be preserved.

1. **Separate verified proof evidence from credential bindings.**

   Introduce an explicit verification result containing one evidence record per submitted JWT and a separate ordered list of credential bindings. Preserve the singular signing key on each JWT evidence record. An optional verified-attestation record carries the attester verification result, validated claims, and attested public keys.

   A binding contains its public key, any validated key identifier/DID, and its association with the proof evidence. It must not imply that every attested key signed the outer JWT. Concrete type names can follow the surrounding API; the distinction between evidence and selected bindings is fixed.

   Update the provider's completeness check to compare submitted JWT count with evidence count. Allocate issuance inputs from the selected binding count. Update handlers and signers to consume the selected binding explicitly. Raw outer or attester JWT headers must not override the binding's key or supply another key's DID.

   Migrate custom-verifier fixtures and both issuer consumers in the same API-change unit. The OSS issuer's pre-issuance key acceptance, expected-key checks, and key commitment must consume the same planned bindings used by issuance. Avoid a second, divergent key-selection algorithm in either host.

   Acceptance: existing ordinary JWT issuance and batch tests pass, including request ordering and the existing ordinary-JWT duplicate-key behavior. Attestation remains unsupported during this structural milestone.

2. **Add the shared attestation verifier and purpose-specific trust configuration.**

   Implement the attestation models and verifier in `commonMain`, under the credential-proof area. Keep signature and claim validation in the library. Supply a narrow attester-key/trust resolver that receives the relevant issuer and credential context and returns authorized verification material. Request-supplied identifiers are lookup hints, not authorization to trust a signer.

   Initial adapters cover configured public JWKs, host-resolved crypto2 key references, and X.509 chains anchored in configured roots. Use separate configuration types and instances from client authentication. Reuse low-level certificate and JOSE code; do not invoke the client-attestation claim validator. Do not inherit generic operating-system roots or fetch arbitrary token-supplied URLs.

   Define typed outcomes for invalid evidence, untrusted attesters, and unavailable/misconfigured host trust material. Configuration or KMS failures must reach the existing server-error path instead of being relabeled as an invalid wallet proof. Preserve coroutine cancellation.

   An optional policy interface can evaluate additional certification/status requirements. The default implementation must not fetch certification URLs or claim revocation validation it did not perform. A host that requires such evaluation must configure a capable evaluator before advertising the feature.

   Acceptance: trusted static keys and configured certificate chains work; unknown keys, invalid chains, and a signer trusted only for client authentication fail. Platform-neutral validation compiles for the library's supported targets.

3. **Integrate nested attestation validation into the JWT proof path.**

   Replace the blanket `key_attestation` rejection with the shared verifier while retaining existing outer-proof validation. Validate token shape, explicit type, asymmetric algorithm, signature, timestamps, public key material, and supported claims. Check both signing algorithms against the selected credential configuration. Compare the outer signing key with attested public keys by canonical public-key thumbprint, never by `kid` text alone.

   Reuse `CredentialNonceService` for the attestation and outer proof. Keep the existing reusable-until-expiry nonce behavior. Do not impose the outer JWT's five-minute `iat` policy on an attestation automatically; validate attestation lifetime and any additional freshness policy explicitly.

   Use `ProofType.keyAttestationsRequired` as the source of truth for whether evidence is required. Enforce the configured `key_storage` and `user_authentication` constraints with exact accepted-value matching initially. An empty requirements object still requires evidence. If attestation is optional but supplied, validate it fully. Absence of a configured trust mechanism must never turn supplied evidence into an ignored header.

   Avoid importing client-attestation-specific claims such as mandatory `cnf`, `sub`, or `client_id` into this validator. Keep outer-proof audience/client binding checks in their existing path. Reject unsupported Federation-based evidence explicitly in this release.

   Map malformed, untrusted, mismatched, or expired evidence to `invalid_proof`; map missing/invalid required nonce to `invalid_nonce`. Distinguish these from the operational failures defined in milestone 2.

   Acceptance: valid nested single-key attestation succeeds, required-but-missing evidence fails, an unrelated outer signing key fails, and supplying invalid optional evidence also fails.

4. **Enable multiple bindings from one attested proof.**

   The proposed default issuance policy is to use all accepted distinct keys in the attestation, subject to explicit host limits and supported credential binding representations. Keep the verified evidence and planned credentials separate through the complete provider and signer flow.

   For requests containing attestations, plan bindings in first-occurrence order and collapse repeated public keys by thumbprint, while still validating every submitted proof and every attestation. Preserve evidence associations. Do not change the multiplicity of existing ordinary-JWT-only batches as a side effect of this feature. Test overlapping attested sets and mixed attested/plain JWT inputs explicitly.

   Maintain separate limits for submitted proofs, keys per attestation, and credentials per request. Check key-array limits before restoring every key, then check the complete output plan before any issuance-input allocation. Exceeding a configured output limit rejects the request in this initial implementation; do not silently truncate. The new attestation configuration must provide bounded limits rather than relying on `batch_size` to bound expansion.

   Additional keys cannot inherit the outer signing key's DID. JWK/COSE-capable bindings support the multi-key path directly. For DID-only credential configurations, initially support the single attested signing key with its resolved DID; reject a multi-key request whose additional identifiers cannot be established. Do not invent DIDs or issue unbound credentials to accommodate that request.

   Complete proof, attestation, binding, and limit validation before requesting issuance inputs. Each selected binding gets its own credential and associated per-credential data/status. Treat attester keys as verification material only.

   Acceptance: one JWT covering three distinct accepted keys produces three correctly bound credentials for JWK/COSE-capable formats. A validation failure anywhere produces no issuance inputs or status allocations. Single-key attestation still produces one credential.

5. **Wire trust and policy into issuer implementations.**

   Enterprise issuer2 gets a dedicated key-attestation configuration beside its existing client-authentication configuration. Resolve KMS references and roots in the current issuer/tenant context, and inject them through the library seam. Avoid mutable shared trust state on the global provider. If trust data is cached, scope it by issuer and configuration revision.

   Validate service creation/update and effective credential configuration so requirements cannot be advertised without a usable attestation verifier and policy. Preserve issuer aliases, session handling, usage accounting, status allocation, audit, and webhook behavior, using the selected credential count where per-credential work is required.

   Adapt the OSS issuer's configuration/wiring to the same library contracts. Its expected-key and acceptance/commitment hooks must include every selected attested binding; an attestation must not bypass session key restrictions. Update Swagger/OpenAPI examples and consume those examples from the integration fixtures.

   Acceptance: two issuers with different attester trust settings remain isolated; configuring wallet/client attestation alone does not enable key-attestation trust; host failures are distinguishable from wallet validation failures.

6. **Complete focused validation and usage documentation.**

   Extend the existing library proof and provider suites, with new attestation-verifier tests. Cover protocol shape/policy in common tests where practical, real JWT signatures and certificate chains on JVM, and compile/test coverage for the enabled KMP targets. Add host integration tests for the new configuration and issuance behavior.

   Use the acceptance matrix below as the release gate. Document the wire example, trust adapters, resource limits, one-key/three-key behavior, nonce reuse, key-selection policy, and DID-only limitation. Describe attestation acquisition as a host/wallet integration concern rather than inventing a standard endpoint.

The main implementation locations are relative to the library unless a workspace prefix is shown:

| Area | Existing locations |
|---|---|
| Entry point and result model | `src/commonMain/kotlin/id/walt/openid4vci/proofs/CredentialProofVerifier.kt` |
| JWT verification | `src/commonMain/kotlin/id/walt/openid4vci/proofs/DefaultCredentialProofVerifier.kt` |
| Provider dispatch and counts | `src/commonMain/kotlin/id/walt/openid4vci/core/DefaultOAuth2Provider.kt`, `core/Config.kt` |
| Issuance inputs and bindings | `src/commonMain/kotlin/id/walt/openid4vci/handlers/endpoints/credential/CredentialIssuanceInput.kt` |
| Credential output | `src/commonMain/kotlin/id/walt/openid4vci/handlers/credential/` SD-JWT, W3C JWT, and mdoc signers/handlers |
| Metadata requirements | `src/commonMain/kotlin/id/walt/openid4vci/metadata/issuer/CredentialConfiguration.kt` |
| Existing library regression suites | `DefaultCredentialProofVerifierTest`, `ProviderCredentialProofVerificationTest`, `ProviderCredentialIssuanceTest`, mdoc handler tests |
| Enterprise configuration | `waltid-identity-enterprise/waltid-enterprise-api/src/main/kotlin/id/walt/enterprise/resource/resources/IssuerResources2.kt` and service creation/update configuration |
| Enterprise wiring | `waltid-identity-enterprise/waltid-enterprise-api/src/main/kotlin/id/walt/enterprise/services/issuer2/` provider registry, enterprise service, and OpenAPI examples |
| OSS wiring and pinned keys | `waltid-identity/waltid-services/waltid-issuer-api2/src/main/kotlin/id/walt/issuer2/` protocol service, module wiring, and proof-key hooks |
| Enterprise regression coverage | `waltid-identity-enterprise/waltid-enterprise-integration-tests/` issuer2 fixtures and tests |

| Acceptance scenario | Expected outcome |
|---|---|
| Ordinary JWT, no attestation requirement | Existing successful behavior |
| One JWT, one attested key matching its signer | One correctly bound credential |
| One JWT, three distinct attested keys including its signer | Three correctly bound credentials with supported binding representation |
| Signing key is outside the attested set | `invalid_proof`, no issuance inputs |
| Required attestation absent, including empty requirements object | `invalid_proof` |
| Invalid supplied attestation when optional | `invalid_proof` |
| Bad signature/type/algorithm, private JWK material, empty key list, expired/missing nested expiry | `invalid_proof` |
| Missing or invalid required nonce in either token | `invalid_nonce` |
| Same valid nonce reused before expiry | Accepted under existing nonce policy |
| Unaccepted storage/authentication assurance | `invalid_proof` |
| Attester trusted only for client authentication | Rejected for key attestation |
| Trusted key reference unavailable | Operational/server failure; no trust fallback |
| Keys overlap across attested proofs | All evidence validated; selected bindings follow documented uniqueness/order policy |
| Unsupported additional DID binding or incompatible selected key | Failure before allocation/signing |
| Proof count, per-attestation key count, or output count exceeds its own limit | Failure before issuance-input allocation |
| OSS expected-key/acceptance hook rejects an additional attested key | Complete request rejected before credential construction |
| Enterprise cross-issuer trust attempt | Rejected; no trust material leaks between issuers |

Run Gradle from the unified workspace root. Validation commands:

```bash
./gradlew :waltid-libraries:protocols:waltid-openid4vci:jvmTest
./gradlew :waltid-libraries:protocols:waltid-openid4vci:jsNodeTest
./gradlew :waltid-services:waltid-issuer-api2:test
./gradlew :waltid-enterprise-api:compileKotlin :waltid-enterprise-integration-tests:compileKotlin
./gradlew :waltid-enterprise-integration-tests:test --tests '*Issuer2KeyAttestationIntegrationTest'
./gradlew :waltid-enterprise-integration-tests:issuer2ClientAttestationIntegrationTest
```

`Issuer2KeyAttestationIntegrationTest` runs through the focused `issuer2KeyAttestationIntegrationTest` task. Run additional host batch/status regressions affected by the changed input count, and applicable ABI checks if the module enforces a published API baseline. Report unavailable platform or environment checks explicitly rather than claiming coverage.

Suggested review sequence: evidence/binding API migration; shared verifier and trust adapters; nested validation plus multi-key issuance and library tests; host configuration, integration tests, and documentation. The implementation includes the evidence/binding migration and existing-behavior regression checks.


Validation completed on 2026-09-28:

- Library JVM tests: 443 passed.
- Library JS/Node tests: 367 passed. The unified workspace's Yarn lock check reported a mismatch; tests passed with `-x kotlinStoreYarnLock`, without changing the dependency lock file.
- OSS credential-proof HTTP tests: 3 passed, including rejection/retry and acceptance of every attested binding.
- Enterprise key-attestation integration: passed with separate static-JWK and KMS-backed issuer trust, required/invalid evidence rejection, and three correctly bound credentials from one JWT.
- Enterprise client-attestation regressions: 5 passed.
- Enterprise service-creation and credential-status regressions: 59 passed.
- Enterprise API and OSS issuer compilation passed. Native targets were not exercised on this Linux host.


Review fixes on 2026-09-28:

- The key-attestation adapter rejects ambiguous subjects and unused certificates by checking the complete constructed chain against the supplied leaf-first chain, and restores the exact validated leaf key. Shared chain-builder hardening was initially included but was reverted on 2026-09-29 to keep this feature scoped to key attestation; broader X.509 caller review is deferred.
- Decode OSS service configuration through its primary constructor while preserving Hoplite field names and aliases. Attestation configuration uses the kotlinx decoder; malformed settings cannot fall back to the legacy constructor.
- Enforce optional numeric `nbf`, including fractional dates and clock skew.
- Verify attester signatures locally using exported public material. Retrieval failures remain `KeyAttestationServiceException`; cancellation propagates. Custom trust resolvers must provide exportable public material.
- Permanent regressions cover the forged duplicate-subject chain, disconnected cycles, certificate ordering, actual config-file loading and rejection, `nbf`, local verification, retrieval failure, and cancellation.

Post-fix verification on 2026-09-28, before the shared X.509 rollback: protocol JVM 448 passed; protocol Node 372 passed; OSS issuer 212 passed; X.509 JVM 210 passed and 7 skipped; enterprise key attestation 1 passed and client attestation 5 passed. Enterprise suites were run separately after a concurrent test-server port collision. Node runs retain the existing `-x kotlinStoreYarnLock` workaround without changing the lock file.

X.509 Node: 118 passed and 7 skipped. Two existing RSA chain tests exceeded the module's two-second Mocha timeout on the first run; the full suite passed with a temporary init script setting `useMocha { timeout = "30s" }`, matching the workspace convention. No repository timeout setting was changed.

Rollback verification on 2026-09-29: the shared `X509CertificateChain` source is restored exactly to HEAD, and its new shared-only tests were removed. The unused-certificate regression now exercises the key-attestation adapter. `KeyAttestationX509Test` passed on JVM (2 tests) and Node (2 tests) with the original shared builder; the existing Yarn-lock exclusion was retained. Broader shared X.509/client-attestation hardening remains separate work.
