# CS-02 v1.2 coverage boundary

This assessment uses [CS-02 v1.2 (23 September 2026)](https://github.com/webuild-consortium/wp4-architecture/blob/b63c25a5ef5c0332d929146c5d38b76a10ca52e2/conformance-specs/cs-02-credential-presentation.md),
[HAIP 1.0 §5](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0-final.html#section-5),
and [ETSI TS 119 472-2 V1.2.1 §§6.3–6.4](https://www.etsi.org/deliver/etsi_ts/119400_119499/11947202/01.02.01_60/ts_11947202v010201p.pdf).
The [deployed catalogue](ITB-WALLET.md#deployed-catalogue) records the current suite inventory.
The suite, individual case and normative profile have independent versions.

## Selected live protocol coverage

| Cases | Contract exercised |
| --- | --- |
| VP-001/002 | Signed `x509_hash` SD-JWT presentation with default/explicit encrypted response mode |
| VP-003 | GET request-URI retrieval |
| VP-011/012/013 | POST request-URI retrieval via `openid4vp`, `eu-eaap` and optional `haip-vp`, respectively |
| VP-007 | Remote mdoc interoperability extension, explicitly outside CS-02 v1.2 normative coverage |

The three invocation cases require the credential ID from a successful VCI-006 session.
Before invoking the wallet, the runner checks that the request uses the advertised scheme;
an unrelated offer or scheme fails the case and triggers owned-session cleanup.
Passing requires both wallet success and that exact session's terminal ITB success.
App runs use Android's registered intent filter and the runner operates the wallet’s consent screen. The runner
opens the original URI from the portal's `VP request` field. Camera scanning and browser
invocation are independent of these URL-based cases. For `haip-vp`, the demo
wallet requires a signed Request Object fetched by reference and an encrypted
`direct_post.jwt` response before offering consent.

The deployed VP-011/012/013 descriptions explicitly limit their scope to the
available protocol slice. They identify missing `verifier_info.registrar_dataset`
as a reference-backend dependency and exclude WRPRC chain/signature, registration
binding, service-provider entitlement and registered credential/claim scope checks.
A reference-backend gap is not an exemption from the normative profile.

## Full-profile assessment

| CS-02 requirement | Evidence boundary and remaining work |
| --- | --- |
| §7.1.1; §§5, 8.2: HAIP, ES256/P-256, signed JAR, DCQL | Selected positive live paths exercise these protocols. Existing offline tests cover trusted, untrusted, altered and unsigned requests. They are pinned v1.1 tests, not a complete v1.2 acceptance/rejection matrix. HAIP trusted-authority queries, algorithm negotiation, certificate-profile constraints and all inherited security/privacy provisions need separate qualification. |
| §7.1.2–3; §§6.1–6.2, 8.1: same/cross-device and invocation | Android app runs deliver the three schemes to the wallet through implicit intents. `openid4vp` and `eu-eaap` are mandatory; `haip-vp` is optional. Cross-device scanning, browser session binding and result return need separate evidence. |
| §7.1.4–5; §§6.1.3, 6.2.3, 8.2: request authentication and `x509_hash` only | The deployed cases use signed requests. App runs use the demo wallet's configured trust anchors. SDK diagnostics use the runner's pinned CA or a configured override. The general resolver supports other authenticated client identifiers. An exclusive CS-02 policy, mandatory expiry and the full nonce/replay rejection matrix still need qualification. |
| §7.1.6; §§5, 8.2: `verifier_info` and WRPRC | Not qualified. The current `VerifierInfoItem` model accepts string data and has no `registrar_dataset` format; ETSI requires a non-empty object with registrar fields. The runner does not enforce its presence or validate registration data/WRPRC. Both the shared wallet policy/model and the reference backend need work. Absence must eventually be a rejection, not a skip or fabricated registration fixture. |
| §7.1.7, 9–10; §§6.1.5, 6.2.5: selective disclosure, proof and bindings | The deployed counterpart checks SD-JWT/KB-JWT, nonce/hash audience, `sd_hash`, disclosure and DCQL response arrays. Passing is interoperability evidence for those inputs. Independent negative vectors for unsolicited claims, changed nonce/audience and invalid bindings remain necessary for full-profile qualification. |
| §7.1.8 and MUST NOT auto-consent; §§6.1.4, 6.2.4 | The app runner automates the positive consent path. Independent review of verifier identity, requested claims, disclosure details and accessibility, plus evidence of explicit refusal, is still needed. |
| §7.1.11; §§6.1.6–7, 6.2.6–7, 8.3: response and outcome | Positive encrypted `direct_post.jwt` transmission is exercised. Full form-encoded JWE/state/error submission, redirect acknowledgement, session-fixation protection and user-visible result handling are not established by a terminal ITB pass. |
| §7.2 and §8.4: Verifier role and metadata | The reference verifier is a counterpart, not the product under test. Its nonce, verification, privacy, metadata and deployment obligations need a dedicated Verifier SUT assessment; they cannot be credited to this wallet run. |
| §9: conformance | Full CS-02 v1.2 conformance is not claimed. All wallet requirements and relevant interfaces/flows must be satisfied before that claim is possible. |

Do not impose CS-02-specific restrictions globally on the multi-profile wallet.
Complete registration-data support and an explicit ecosystem policy at the shared
request/trust boundary, then qualify its consumers. Preserve default protocol
compatibility and add independent rejection vectors for the scoped policy.
The shared registration and disclosure policy is tracked in [WAL-1845](https://linear.app/walt-new/issue/WAL-1845).
Qualification of the remaining deployed wallet cases is tracked in [WAL-1868](https://linear.app/walt-new/issue/WAL-1868).

The [wallet case ledger](ITB-WALLET.md#wallet-case-ledger) distinguishes the disabled
base-suite VP-004 from the enabled Trust Framework VP-004 whose last two attempts
timed out before wallet interaction. It also covers the other deployed suites.
