# Deployed ITB runner baseline — 2026-09-22

The standalone `itbWallet` CLI completed all **21 deployed cases** on clean commit
`c4ed00f788fcbc5e4eba51c2ab80361c144df9b0`: **9 passed, 6 failed at wallet
execution and 6 failed during interaction acquisition**. Every case has a
correlated terminal ITB XML report; no cleanup failed. The process exited 1.
A pass requires both wallet success and terminal ITB `SUCCESS`.

The run used the production JVM wallet adapter, its authenticated Playwright
bridge and synthetic wallet data. Production code includes the main merge of
[#2168](https://github.com/walt-id/waltid-identity/pull/2168); outstanding production
fixes remain external. The hosted live workflow has not run. JVM protocol results
do not qualify native delivery, consent UX or full normative EUDI/SCA assurance.

## Standalone case results

`UNDEFINED` records a session stopped after failure; it is not a pass. Interaction
errors have `adapterInvoked=false` and must not be described as wallet failures.
This is one complete run, without replacing failures with successful retries.

| Case | Outcome / phase | Terminal ITB | Runtime version | Session |
| --- | --- | --- | --- | --- |
| `tc_vci_001` | `PASSED` / `VERDICT` | `SUCCESS` | 1.0 | `0ba786b0-1ece-4e7d-9a04-b5b68a4dfd64` |
| `tc_vci_002` | `PASSED` / `VERDICT` | `SUCCESS` | 1.0 | `bd75f45b-250a-4c6b-a1ee-194af7c50d93` |
| `tc_vci_003` | `PASSED` / `VERDICT` | `SUCCESS` | 1.0 | `f745adb3-980b-4c0f-a3bd-4678cbf906a8` |
| `tc_vci_005` | `WALLET_FAILED` / `WALLET` | `UNDEFINED` | 1.0 | `f4bb4f94-88ef-48f5-83c6-48b65997c689` |
| `tc_vci_006` | `PASSED` / `VERDICT` | `SUCCESS` | 1.0 | `700ebcfc-181f-4a12-9fa8-03f1d87e8e88` |
| `tc_vci_007` | `PASSED` / `VERDICT` | `SUCCESS` | 1.0 | `4a4323af-c928-4fb1-92b5-5673044bb35f` |
| `tc_vci_008` | `WALLET_FAILED` / `WALLET` | `UNDEFINED` | 1.0 | `b99c91ef-4be5-494b-b6da-f36dc360acab` |
| `tc_vp_001` | `ERROR` / `INTERACTION` | `UNDEFINED` | 1.0 | `356d99b8-d835-4f9b-9b1b-b65f6914fc48` |
| `tc_vp_002` | `PASSED` / `VERDICT` | `SUCCESS` | 1.0 | `f5316e3c-58b1-4d68-9fa0-051ba29ec7d5` |
| `tc_vp_003` | `PASSED` / `VERDICT` | `SUCCESS` | 1.0 | `21d13278-90d0-4fc0-b5c5-b446ead511a6` |
| `tc_vp_007` | `WALLET_FAILED` / `WALLET` | `UNDEFINED` | 1.0 | `409c8bae-b223-41bc-9bf1-ece6b676ab6a` |
| `tc15` | `ERROR` / `INTERACTION` | `FAILURE` | 1.6 | `b3b2e9b6-1cae-4c59-bf7a-465a61227229` |
| `ts12_issue_01` | `PASSED` / `VERDICT` | `SUCCESS` | 1.0 | `eb039b41-1c4b-40d8-ab51-1f47756d25c3` |
| `ts12_issue_02` | `ERROR` / `INTERACTION` | `UNDEFINED` | 1.0 | `69f1c249-ed98-4ff2-807d-a92b923b8b9d` |
| `ts12_issue_03` | `PASSED` / `VERDICT` | `SUCCESS` | 1.0 | `0f13d26d-2040-49fa-805d-86ddda64cf2c` |
| `ts12_pay_01` | `WALLET_FAILED` / `WALLET` | `UNDEFINED` | 1.0 | `7c9eea10-338f-412e-92b9-c9cb1de60404` |
| `ts12_pay_02` | `WALLET_FAILED` / `WALLET` | `UNDEFINED` | 1.0 | `9f40d6bb-6eb2-43c7-9ab0-441d70f294c2` |
| `ts12_pay_03` | `WALLET_FAILED` / `WALLET` | `UNDEFINED` | 1.0 | `109382cc-e6c9-4d8d-8127-c04317a41ea1` |
| `ts12_pay_dc_api_01` | `ERROR` / `INTERACTION` | `FAILURE` | 1.0 | `d5e23424-6858-4481-bf5c-4fb3f309f895` |
| `ts12_pay_dc_api_02` | `ERROR` / `INTERACTION` | `FAILURE` | 1.0 | `8880d6a8-f4a1-4466-9bb1-f442c57e75cc` |
| `ts12_pay_dc_api_03` | `ERROR` / `INTERACTION` | `FAILURE` | 1.0 | `19da1bc5-f837-46f3-a2df-79cfc69bca3b` |

## Automation gaps

- **Four DC API cases:** REST-started CS-07 and all three TS12 DC API sessions
  finish with ITB `FAILURE` before a pending wallet interaction is available.
  The wallet adapter is never invoked. For CS-07, a controlled portal-started
  comparison (`032a6a0c-b7a8-428f-ab20-e032be0b19b1`) exposes the expected DC API
  script and waits; that diagnostic session was then stopped. This establishes
  a REST-versus-interactive execution difference. GITB documents automatic
  completion of background interactions without a suitable timeout or handler
  ([interaction semantics](https://www.itb.ec.europa.eu/docs/tdl/latest/constructs/index.html#background-execution-and-timeouts)).
  The deployed test definition was not available to confirm the exact setting.
  Resolve the test-bed interaction contract or implement and qualify interactive
  session startup before claiming automated wallet coverage for these four cases.
- **Portal reliability:** VP001 and TS12 issuance 02 timed out during interaction
  acquisition in this run, despite passing earlier. Targeted diagnostic retries
  passed, but do not erase these failures. The intermittent cause remains open.
- **Hosted execution:** seven repository Actions secrets and organisation variable
  are configured, consistent with the existing conformance jobs; no dedicated
  environment is used. GitHub rejects dispatch while the workflow is absent from
  the default branch. Its first hosted smoke and full matrix remain pending.

## Targeted portal retry

Two diagnostic retries passed VCI006, VP001 and TS12 issuance 02 (3/3 each).
They used `c4ed00f788fcbc5e4eba51c2ab80361c144df9b0+dirty` with temporary,
message-free exception-location logging; no wallet or browser logic changed.
The last retry's sessions are below. The diagnostic logging was then removed.
Neither retry establishes the intermittent timeout's cause or replaces the
complete-run results.

| Case | Wallet / terminal ITB | Session |
| --- | --- | --- |
| `tc_vci_006` | Passed / `SUCCESS` | `7b7dee46-db63-408e-ac50-e8210879c24d` |
| `tc_vp_001` | Passed / `SUCCESS` | `bc0d9e7f-ba4e-4e57-8bcc-d1474b0ca7bf` |
| `ts12_issue_02` | Passed / `SUCCESS` | `500182aa-f65e-4b70-9dc8-045147682aa2` |

## Confirmed dependencies and open gaps

- **WAL-896 / [#2141](https://github.com/walt-id/waltid-identity/pull/2141):**
  Earlier interactive development runs of CS-07 and all three TS12 DC API cases
  reached production signed-protocol dispatch
  and failed with `UnsupportedDcApiProtocolException` for `openid4vp-v1-signed`.
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
regression test covers this observed combination. These development results
are separate from the standalone matrix above.

## Remaining acceptance

Keep the PR draft. Resolve the DC API startup contract and portal reliability,
qualify the hosted workflow after default-branch registration, and rerun all 21
cases after the external wallet and issuer fixes. Failures, timeouts and
unexecuted cases remain non-passing; WAL-1423 stays open until the matrix passes.

The earlier [interactive development evidence](https://github.com/walt-id/waltid-identity/blob/cd2aab80ac701789853d41f517f300a01f3080ac/waltid-services/waltid-openid4vp-conformance-runners/docs/ITB-BASELINE.md)
records 11 passes and 10 wallet failures through a temporary JVM probe with
separate portal control. It establishes the signed DC API and issuer findings,
but is not interchangeable with the standalone results above. Authorization-code
and PAR handling, pre-authorized PIN issuance and VP002 response encryption have
also passed in standalone execution. The explicit verifier trust anchor remains
independently sourced; see [CA provenance](../src/main/resources/itb/README.md).
