# Deployed ITB runner baseline — 2026-09-22

The standalone `itbWallet` CLI completed all **21 deployed cases** on clean commit
`7d5eb3b3d79cee35d445627cb6edd65e4943a3c1`: **11 passed and 10 failed in
production wallet execution**. Every case reached the wallet and has a correlated
terminal ITB XML report. There were **no orchestration errors, skipped cases or
cleanup failures**. The process exited 1 because the wallet failures remain real
failures. A pass requires both wallet success and terminal ITB `SUCCESS`.

The run used authenticated, interactive portal sessions and synthetic wallet
data. Each session ID was captured before starting its test steps. Offers and
requests came from that session's dialog; REST supplied status, terminal reports
and owned-session cleanup. This avoids the deployed DC API background-interaction
skip and the session-list navigation races found during qualification.

Production code includes the main merge of
[#2168](https://github.com/walt-id/waltid-identity/pull/2168). Outstanding production
fixes remain external. JVM protocol results do not qualify native delivery,
consent UX or full normative EUDI/SCA assurance.

## Standalone case results

`UNDEFINED` records a session stopped after wallet failure; it is not an ITB
assertion failure or a pass. This table contains one complete run, without
replacing failures with successful retries.

| Case | Wallet outcome | Terminal ITB | Runtime version | Session |
| --- | --- | --- | --- | --- |
| `tc_vci_001` | `PASSED` | `SUCCESS` | 1.0 | `c7bfa6ee-b3e2-4c70-b572-32cd31b01842` |
| `tc_vci_002` | `PASSED` | `SUCCESS` | 1.0 | `6d526f02-533b-4a70-874a-99f24ea86a69` |
| `tc_vci_003` | `PASSED` | `SUCCESS` | 1.0 | `34f7d5d1-32ad-42b8-8e83-11c0b8e54cf2` |
| `tc_vci_005` | `WALLET_FAILED` | `UNDEFINED` | 1.0 | `360d4eb2-b00a-4ec0-b3eb-20ad37abe126` |
| `tc_vci_006` | `PASSED` | `SUCCESS` | 1.0 | `50808266-3df4-454d-b6fb-d0cb535fd340` |
| `tc_vci_007` | `PASSED` | `SUCCESS` | 1.0 | `6c94df99-b430-40a5-90df-bbbd2533380a` |
| `tc_vci_008` | `WALLET_FAILED` | `UNDEFINED` | 1.0 | `d73be736-89c5-438b-a68b-cd3bbc99e196` |
| `tc_vp_001` | `PASSED` | `SUCCESS` | 1.0 | `f006551c-e085-4b1d-9662-dd430cb0575f` |
| `tc_vp_002` | `PASSED` | `SUCCESS` | 1.0 | `d94613ab-21de-4e22-b672-7f93931fecff` |
| `tc_vp_003` | `PASSED` | `SUCCESS` | 1.0 | `7869c1f8-9979-4162-865d-5ec5c99ea1e0` |
| `tc_vp_007` | `WALLET_FAILED` | `UNDEFINED` | 1.0 | `8ca6f171-5e1d-456c-87e6-8ce4dfa76eed` |
| `tc15` | `WALLET_FAILED` | `UNDEFINED` | 1.6 | `f98caa55-533a-459b-a995-32260e9e648f` |
| `ts12_issue_01` | `PASSED` | `SUCCESS` | 1.0 | `e15141d0-63ff-42f8-984c-d45b219b9ea9` |
| `ts12_issue_02` | `PASSED` | `SUCCESS` | 1.0 | `715f6a30-e802-46ad-8695-b8574b68c02c` |
| `ts12_issue_03` | `PASSED` | `SUCCESS` | 1.0 | `6aaef32b-8119-4077-b718-5aab706fb4aa` |
| `ts12_pay_01` | `WALLET_FAILED` | `UNDEFINED` | 1.0 | `ef3e420f-93ca-40fc-9392-2a123e1200a1` |
| `ts12_pay_02` | `WALLET_FAILED` | `UNDEFINED` | 1.0 | `a5f19b66-c89f-4b34-95a2-63a370e6baa5` |
| `ts12_pay_03` | `WALLET_FAILED` | `UNDEFINED` | 1.0 | `95c939d9-8dae-4dfd-ad0a-88e8b795ae7c` |
| `ts12_pay_dc_api_01` | `WALLET_FAILED` | `UNDEFINED` | 1.0 | `912fbf16-7e92-4685-9caa-cad9c3d54345` |
| `ts12_pay_dc_api_02` | `WALLET_FAILED` | `UNDEFINED` | 1.0 | `28a6f651-ba07-4215-b812-600fffe1c987` |
| `ts12_pay_dc_api_03` | `WALLET_FAILED` | `UNDEFINED` | 1.0 | `836888dd-6500-46aa-b4a6-167360377cfc` |

## Confirmed dependencies and open gaps

- **WAL-896 / [#2141](https://github.com/walt-id/waltid-identity/pull/2141):**
  CS-07 and all three TS12 DC API cases reach production signed-protocol dispatch
  and fail with `UnsupportedDcApiProtocolException` for `openid4vp-v1-signed`.
  VP007 separately rejects verifier mdoc `deviceauth_alg_values` `[-7, -35]`:
  the baseline P-256 wallet advertises ESP256 (`-9`). The inspected #2141 head
  `04802383782ce3cf76a5d224519ad529ae290bc1` adds ES256 (`-7`) support alongside
  ESP256. This confirms the dependency, not that its complete live matrix passes.
  That PR also adds a `clientIdTrustConfiguration` parameter to the DC API preview
  handler. After it merges, pass the runner's existing pinned reference CA through
  that parameter before rerunning signed X.509 DC API cases. The current baseline
  has no such parameter; this runner does not copy or reflectively invoke future APIs.
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
regression test covers this observed combination. These development results
are separate from the standalone matrix above.

## Execution and remaining acceptance

All five SD-JWT issuance cases, VP001/002/003 and all three TS12 issuance cases
passed. These cover the reference authorization-code/PAR flow, transaction-code
issuance and **VP002 response encryption**. The independently sourced verifier
trust anchor is documented in [CA provenance](../src/main/resources/itb/README.md).

The interactive bridge also passed a targeted six-case run before the full
matrix: four passes and the two expected signed DC API wallet failures. Offline
browser contracts cover exact suite selection, interactive mode, request-versus-QR
labels, stale dialogs and session ownership. They do not replace live evidence.

The live workflow uses three repository secrets (organisation API key and portal
login) and the organisation ID variable, with no dedicated environment. Its first
hosted run remains pending: GitHub requires the workflow on the default branch
before manual dispatch. After it lands, run the issuance/presentation smoke and
then the complete matrix, as described in the [operator guide](ITB-WALLET.md).

Keep the PR draft as requested. Resolve the external wallet and issuer blockers,
apply the DC API trust wiring once its API becomes available, and rerun against
the actual merged revisions. Failures, timeouts and unexecuted cases must remain
non-passing; WAL-1423 stays open until the required matrix passes.
