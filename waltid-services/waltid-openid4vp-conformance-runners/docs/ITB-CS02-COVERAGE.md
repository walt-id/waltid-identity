# CS-02 v1.2 coverage boundary

This assessment uses [CS-02 v1.2 (23 September 2026)](https://github.com/webuild-consortium/wp4-architecture/blob/b63c25a5ef5c0332d929146c5d38b76a10ca52e2/conformance-specs/cs-02-credential-presentation.md),
[HAIP 1.0 §5](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0-final.html#section-5),
and [ETSI TS 119 472-2 V1.2.1 §§6.3–6.4](https://www.etsi.org/deliver/etsi_ts/119400_119499/11947202/01.02.01_60/ts_11947202v010201p.pdf).
The authenticated ITB catalogue was inspected on 2026-11-06. Suite version,
case version and normative profile version are separate identities.

## Selected live protocol coverage

| Cases | Contract exercised |
| --- | --- |
| VP-001/002 | Signed `x509_hash` SD-JWT presentation with default/explicit encrypted response mode |
| VP-003 | GET request-URI retrieval |
| VP-011/012/013 | POST request-URI retrieval via `openid4vp`, `eu-eaap` and optional `haip-vp`, respectively |
| VP-007 | Remote mdoc interoperability extension, explicitly outside CS-02 v1.2 normative coverage |

The three invocation cases require VCI-006's issued PID. Before invoking the
wallet, the runner checks that the portal-supplied request uses the advertised scheme;
an unrelated offer or scheme fails the case and triggers owned-session cleanup.
Passing requires both wallet success and that exact session's terminal ITB success.
It does not prove operating-system URL registration, QR scanning or browser-to-app
delivery: the harness passes the portal-supplied URL to the shared wallet directly.

The deployed VP-011/012/013 descriptions explicitly limit their scope to the
available protocol slice. They identify missing `verifier_info.registrar_dataset`
as a reference-backend dependency and exclude WRPRC chain/signature, registration
binding, service-provider entitlement and registered credential/claim scope checks.
A reference-backend gap is not an exemption from the normative profile.

## Full-profile assessment

| CS-02 requirement | Evidence boundary and remaining work |
| --- | --- |
| §7.1.1; §§5, 8.2: HAIP, ES256/P-256, signed JAR, DCQL | Selected positive live paths exercise these protocols. Existing offline tests cover trusted, untrusted, altered and unsigned requests. They are pinned v1.1 tests, not a complete v1.2 acceptance/rejection matrix. HAIP trusted-authority queries, algorithm negotiation, certificate-profile constraints and all inherited security/privacy provisions need separate qualification. |
| §7.1.2–3; §§6.1–6.2, 8.1: same/cross-device and invocation | Three URL schemes are exercised at the shared-wallet boundary. `openid4vp` and `eu-eaap` are mandatory; `haip-vp` is optional. Native routing, both device flows, browser session binding and result return remain unqualified by this runner. |
| §7.1.4–5; §§6.1.3, 6.2.3, 8.2: request authentication and `x509_hash` only | The runner requires signed requests and supplies an independently pinned CA. The general resolver also supports other authenticated client identifiers; this runner has no exclusive CS-02 policy. Audience and supplied time claims are validated, but mandatory expiry presence and the full nonce/replay rejection matrix are not proved. Positive hash cases cannot prove rejection of other prefixes. |
| §7.1.6; §§5, 8.2: `verifier_info` and WRPRC | Not qualified. The current `VerifierInfoItem` model accepts string data and has no `registrar_dataset` format; ETSI requires a non-empty object with registrar fields. The runner does not enforce its presence or validate registration data/WRPRC. Both the shared wallet policy/model and the reference backend need work. Absence must eventually be a rejection, not a skip or fabricated registration fixture. |
| §7.1.7, 9–10; §§6.1.5, 6.2.5: selective disclosure, proof and bindings | The deployed counterpart checks SD-JWT/KB-JWT, nonce/hash audience, `sd_hash`, disclosure and DCQL response arrays. Passing is interoperability evidence for those inputs. Independent negative vectors for unsolicited claims, changed nonce/audience and invalid bindings remain necessary for full-profile qualification. |
| §7.1.8 and MUST NOT auto-consent; §§6.1.4, 6.2.4 | Not qualified. The unattended harness selects credentials programmatically. Verifier identity, claims, disclosure details, explicit consent/refusal and accessibility require product UI evidence. |
| §7.1.11; §§6.1.6–7, 6.2.6–7, 8.3: response and outcome | Positive encrypted `direct_post.jwt` transmission is exercised. Full form-encoded JWE/state/error submission, redirect acknowledgement, session-fixation protection and user-visible result handling are not established by a terminal ITB pass. |
| §7.2 and §8.4: Verifier role and metadata | The reference verifier is a counterpart, not the product under test. Its nonce, verification, privacy, metadata and deployment obligations need a dedicated Verifier SUT assessment; they cannot be credited to this wallet run. |
| §9: conformance | Full CS-02 v1.2 conformance is not claimed. All wallet requirements and relevant interfaces/flows must be satisfied before that claim is possible. |

Do not impose CS-02-specific restrictions globally on the multi-profile wallet.
Complete registration-data support and an explicit ecosystem policy at the shared
request/trust boundary, then qualify its consumers. Preserve default protocol
compatibility and add independent rejection vectors for the scoped policy.
The broader profile work belongs with [WAL-1534](https://linear.app/walt-new/issue/WAL-1534/itb-follow-up-i).

## Other newly deployed cases

| Case family | Decision |
| --- | --- |
| CS-07 `tc16` | Deferred. It uses `navigator.credentials.create()` / `openid4vci-v1` with an authorization-code PID offer and an issuance-status capability. The existing bridge handles presentation descriptors, not DC API issuance. Extracting an offer and calling ordinary issuance would not prove native DC API delivery. |
| CS-10 `rv_iss_01`, `rv_iss_02` | Deferred. The first requires two offers, shared-list/different-index assertions and authenticated status-token validation; the second requires a revoked-credential result. The current one-interaction issuance success check cannot establish either contract. |
| TS12 `w01`, `w02`, `w03` | Excluded from the Wallet SUT inventory: these are Issuer/Verifier SUT cases using the reference wallet as counterpart. |
| Trust Framework, QES and other suites | Remain separate coverage work. Similar labels do not establish matching trust configuration, credentials or interaction contracts. |
| Six TS12 payment cases | Retained as local operator-assisted coverage. Per-use native authentication and genuine user approval remain required; hosted CI cannot supply that evidence. |
