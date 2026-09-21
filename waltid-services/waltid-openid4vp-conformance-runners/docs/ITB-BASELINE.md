# Deployed ITB development baseline — 2026-09-21

The initial WAL-1423 matrix contains 21 deployed wallet cases: **11 passed and
10 failed at the wallet interaction**. Every listed session has an authenticated,
terminal ITB XML report correlated to its case and session ID. A pass requires
both successful wallet execution and terminal ITB `SUCCESS`.

Production code is based on `78e6929fc360729feb45af81697405ca3cf01fa1`, the main
merge of [#2168](https://github.com/walt-id/waltid-identity/pull/2168). The runner
was developed on top of `4bf1120b3de73e8240531380a108a01945d1edc0` with uncommitted
adapter changes between runs. These are development qualification results, not
an immutable final-head CLI or hosted CI run.

The production `ItbWalletDriver` was exercised through a temporary JVM probe;
Cua drove the authenticated portal interactions. Sessions used synthetic wallet
data. Raw requests, credentials and service logs are not included here. Native
platform delivery, consent and normative SCA authentication are not qualified.

## Case results

`UNDEFINED` means the session was stopped after a wallet failure; it is not an
ITB assertion failure or a pass. The two failed REST follow-up sessions reached
ITB `FAILURE` after the interaction window elapsed. Their wallet failures were
observed independently. No required cases are skipped or allowlisted.

| Case | Wallet outcome | Terminal ITB | Runtime case version | Session |
| --- | --- | --- | --- | --- |
| `tc_vci_001` | Passed | `SUCCESS` | 1.0 | `f2e3cf50-fc3a-4fea-be98-2e85212eb6ac` |
| `tc_vci_002` | Passed | `SUCCESS` | 1.0 | `05d1a8ca-b6ef-4693-900d-4b2628a9a0b2` |
| `tc_vci_003` | Passed | `SUCCESS` | 1.0 | `bee4c6f9-e5e3-4e6d-99ca-9cf69d2bfa8d` |
| `tc_vci_005` | mdoc COSE key decoding failed | `UNDEFINED` | 1.0 | `16eb62bf-4d5a-47d4-a326-3f6f79514d8a` |
| `tc_vci_006` | Passed | `SUCCESS` | 1.0 | `89cf59d1-6bea-4be6-b403-3a716cfa2431` |
| `tc_vci_007` | Passed | `SUCCESS` | 1.0 | `081873eb-a839-4a55-86df-c0a085a9d43d` |
| `tc_vci_008` | mdoc COSE key decoding failed | `FAILURE` | 1.0 | `8de6ee3a-1a87-4ea5-b875-e03ab74b01fb` |
| `tc_vp_001` | Passed | `SUCCESS` | 1.0 | `be34c50b-474f-40a6-bcc0-068c350cd7b9` |
| `tc_vp_002` | Passed | `SUCCESS` | 1.0 | `279e3e5d-fabd-495a-ab52-6fb005b4831c` |
| `tc_vp_003` | Passed | `SUCCESS` | 1.0 | `5c2089d0-cf22-4b07-8ae0-2b19e8a25b02` |
| `tc_vp_007` | `vp_formats_not_supported` | `FAILURE` | 1.0 | `cf89c17c-c7b3-4870-ac77-216412d1e2d8` |
| `tc15` | Signed DC API protocol unsupported | `UNDEFINED` | 1.6 | `0029327a-1089-41b9-a73b-1085289571f5` |
| `ts12_issue_01` | Passed | `SUCCESS` | 1.0 | `4a52016e-7b46-43d5-9a06-9f61a208d16b` |
| `ts12_issue_02` | Passed | `SUCCESS` | 1.0 | `e4347af5-6a8f-4d98-90fa-e73bb7bfddd4` |
| `ts12_issue_03` | Passed | `SUCCESS` | 1.0 | `1b63f83e-5909-4a43-8e04-0718420aaf9d` |
| `ts12_pay_01` | Request encryption key required (HTTP 400) | `UNDEFINED` | 1.0 | `81411bd0-39e4-498b-be25-efd0cb038fa4` |
| `ts12_pay_02` | Request encryption key required (HTTP 400) | `UNDEFINED` | 1.0 | `72dd3ef2-c230-4e38-a947-fd3ce3939a52` |
| `ts12_pay_03` | Request encryption key required (HTTP 400) | `UNDEFINED` | 1.0 | `ab991d32-5442-4041-b440-78b870c8b55e` |
| `ts12_pay_dc_api_01` | Signed DC API protocol unsupported | `UNDEFINED` | 1.0 | `3da55426-fc03-4aac-882f-15e2b8be80ad` |
| `ts12_pay_dc_api_02` | Signed DC API protocol unsupported | `UNDEFINED` | 1.0 | `2a9c5629-6e79-4c42-a3cf-eaf9c9e091da` |
| `ts12_pay_dc_api_03` | Signed DC API protocol unsupported | `UNDEFINED` | 1.0 | `73d26917-d334-4e54-b0f0-83eb7d36bad6` |

## Confirmed dependencies and open gaps

- **WAL-896 / [#2141](https://github.com/walt-id/waltid-identity/pull/2141):**
  CS-07 and all three TS12 DC API cases reach production signed-protocol dispatch
  and fail with `UnsupportedDcApiProtocolException` for `openid4vp-v1-signed`.
  VP007 separately rejects verifier mdoc `deviceauth_alg_values` `[-7, -35]`:
  the baseline P-256 wallet advertises ESP256 (`-9`). The inspected #2141 head
  `511e0b779f236396f0019dcf12493b5754c08dee` adds ES256 (`-7`) support alongside
  ESP256. This confirms the dependency, not that its complete live matrix passes.
- **Reference-issuer mdoc encoding:** VCI005 and VCI008 fail parsing the issued
  `DeviceKeyInfo.CoseKey`. Fresh captures confirmed that the issuer encodes COSE
  label `2` (`kid`) as a CBOR text string. [RFC 9052 section 7.1](https://www.rfc-editor.org/rfc/rfc9052.html#section-7.1)
  requires a byte string. Labels `-2` and `-3` (P-256 coordinates) are correctly
  encoded as 32-byte strings. This defect belongs to the reference issuer;
  changing the wallet to accept malformed COSE keys is not part of this runner.
  No existing owning ticket was confirmed. WAL-781 concerns a different issue.
- **TS12 encrypted request delivery:** the three ordinary payment requests return
  HTTP 400 requiring request-encryption keys in the wallet metadata for POST
  Request Object delivery. The baseline advertises algorithms without supplying
  a request-decryption key. This is separate from response encryption, and is not
  established as covered by #2141. Assign the request-encryption work separately.
- **Proof `iss`:** [#2246](https://github.com/walt-id/waltid-identity/pull/2246)
  remains an external fix for the historical local proof assertion. All five
  deployed SD-JWT VCI cases passed without it; it is not a demonstrated blocker
  for this deployed matrix. [#2222](https://github.com/walt-id/waltid-identity/pull/2222)
  remains independent identity/recovery work.

## Targeted mdoc confirmation

The following follow-ups used the same production wallet baseline plus a temporary
HTTP response observer that recorded only CBOR field types and coordinate lengths.
Both credential endpoints returned HTTP 200 and malformed `kid` values. No raw
credential, key value or account secret is included in this evidence.

| Case | Session | Wallet | Terminal ITB |
| --- | --- | --- | --- |
| VCI005 | `5fc7e07d-a88f-463b-9d17-57621115a08a` | Credential parsing failed | `SUCCESS` |
| VCI008 | `b6df33f7-44d4-4b19-b9e8-b3831b13da68` | Credential parsing failed | `SUCCESS` |

An issuer-side ITB success therefore does not prove successful wallet receipt.
The runner correctly retains `WALLET_FAILED` alongside that ITB verdict; a
regression test covers this observed combination. The overall matrix remains
11 passing and 10 non-passing cases.

An earlier diagnostic attempt, VCI008 session
`e8d9b773-20a4-46bf-aea8-258dff8126d3`, failed in ITB's QR decoding step before an
offer was available. The subsequent run above succeeded at setup. Treat that
attempt as a transient test-bed setup failure, not another wallet decoding result.

## Resolved uncertainties and remaining acceptance

VCI001/002/003 and SCA issuance 03 completed the real authorization-code flow,
including production PAR handling and the reference issuer's direct redirect.
VCI007 completed issuance with the transaction code supplied by its ITB session.
VP001/002/003 completed real presentation; **VP002 response encryption passes**.
VP007's current signed request actually uses `direct_post.jwt` despite its
portal title saying `direct_post`; its DCQL requests PID mdoc claims, so VCI008
is its issuance prerequisite.

The explicit test trust anchor was independently sourced from the official
EUDI wallet and matched to the actual verifier chain; see the
[CA provenance](../src/main/resources/itb/README.md). Request-provided chains
were not automatically trusted.

A separate positive check verified REST start → exact-session portal download →
production issuance → portal completion → terminal REST report `SUCCESS` for
VCI006, session `6a82883b-f6e6-4d8f-8855-938037cb84b9` (2026-09-21 18:33 UTC).
This validates the interaction contract; the standalone Playwright orchestration
and manually dispatched GitHub workflow still need final-build qualification.

Keep the PR draft. Configure CI secrets only through an authorized secret-management
path, exercise the combined entry point, and rerun the complete matrix after
external fixes merge. Ticket completion still requires all 21 deployed cases to
pass; these findings and supporting local checks do not close WAL-1423.
