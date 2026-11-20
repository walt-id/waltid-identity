# Operator-assisted Android ITB execution

The attended path drives the actual Compose Wallet Preview Debug app on a
physical Android phone. The operator reviews the payee and amount in the app and
confirms each transaction with fresh biometric authentication. The hardware-backed
test keys allow 10-second biometric reuse for ordinary signing. The test
uses the same browser and provider entry points as the unattended runs for DC API
interactions. The desktop retains portal and organisation credentials, ITB session
correlation and cleanup. The installed app retains its own keys and credentials.

Use a dedicated test phone with a screen lock and enrolled strong biometrics.
Initialize Chrome and select it as the browser. Build Preview Debug and its
instrumentation APK from the same checkout as the coordinator. The Preview Debug
trust flag pins the TS-12 credential signing key from the reference issuer's
[published ECC certificate](https://github.com/EWC-consortium/ewc-wallet-conformance-backend/blob/c53f009711c605c0df5ab0d8eb46b922b3a7501a/x509EC/client_certificate.crt).
The app still verifies the issued credential and resolves its payment display
metadata. The reference issuer accepts the [synthetic key attestation](ITB-WALLET.md)
used for interoperability tests.

```bash
./gradlew :waltid-applications:waltid-wallet-demo-compose:androidApp:assemblePreviewDebug \
  :waltid-applications:waltid-wallet-demo-compose:androidApp:assemblePreviewDebugAndroidTest \
  -PenableAndroidBuild=true -PwalletSigningProtectionMode=required -PitbDemoPaymentTrust=true

ITB_CASES=ts12_pay_01,ts12_pay_02,ts12_pay_03,ts12_pay_dc_api_01,ts12_pay_dc_api_02,ts12_pay_dc_api_03 \
  scripts/itb/android-demo.sh attended <physical-serial>
```

Provide the usual [ITB environment](ITB-WALLET.md#hosted-cases). Use a fresh
Preview installation. Select **Use PIN only** during the optional biometric
unlock setup. The instrumented setup selects hardware-backed signing with timed approval.
Follow the on-device prompts to create the key. For each payment,
review the transaction and approve it on the phone. The selected payments pull in
their issuance prerequisites, and every case reports its own exact ITB session.
For a watched run of all 27 selected cases on the phone, leave `ITB_CASES` unset,
set `ITB_SHOW_BROWSER=true` and open `android device hub --device <physical-serial>`.
The desktop browser shows the portal. The mirror shows the wallet, Android Chrome
and provider handoffs. Complete biometric prompts on the phone itself.

Reports identify the attended demo mode and both APK digests. The optional
`ItbNativeWalletDeviceTest` SDK fixture is also available for protocol diagnostics
with a physical key. Historical references to that fixture cover SDK signing in
isolation from the demo app's review screen.

## SCA-User audience compatibility

[Rulebook PR #307](https://github.com/webuild-consortium/webuild-attestation-rulebooks-catalog/pull/307)
removed the requirement for an issuer-signed SCA-User `aud` containing the
issuing bank's RP URL and the corresponding verifier check. The
[merged SCA-User rulebook](https://github.com/webuild-consortium/webuild-attestation-rulebooks-catalog/blob/adae9cb41c603724f68462ef23a653190d65c08f/rulebooks/rb-sca-user/README.md)
retains the issuing-bank restriction. Section 7.2 calls for Embedded Disclosure
Policy (EDP), while Section 7.4 still defines no policy.

[CS-12 v1.2](https://github.com/webuild-consortium/wp4-architecture/blob/d6603effe714411d5dd7e9542d4d7b7d9fe11426/conformance-specs/cs-12-sca-payments.md)
still specifies credential audience checks and excludes EDP issuance and
verification. Its audience restriction also covers SCA-IBAN for
`login_risk_transaction` ([WAL-1627](https://linear.app/walt-new/issue/WAL-1627/add-ts12-transaction-type-urneudiscalogin-risk-transaction1)).
[Architecture #381](https://github.com/webuild-consortium/wp4-architecture/issues/381)
tracks the unresolved RP-permission contract and the conflict with
[ETSI TS 119 472-1 V1.2.1 §5.2.8.1](https://www.etsi.org/deliver/etsi_ts/119400_119499/11947201/01.02.01_60/ts_11947201v010201p.pdf#page=31).
Qualifying RP permission requires an agreed way to authenticate and authorize a
requesting RP before disclosure, with acceptance and rejection cases.

The separate reference issuer/verifier interoperability failure in
[ITB #58](https://github.com/webuild-consortium/wp4-interop-test-bed/issues/58)
was resolved after an attended retest on 10 November 2026. Fresh `ts12_issue_02`,
`ts12_pay_02` and `ts12_pay_dc_api_02` each achieved wallet success and correlated
terminal ITB `SUCCESS`, using the Android fixture at
[`9b9df335`](https://github.com/walt-id/waltid-identity/commit/9b9df335ad15c1ccd537b441bb14958fd7b314a4)
with per-use biometric signing and synthetic key attestation. An issuance
diagnostic observed an HTTPS credential audience. To qualify the updated profile
after backend deployment, inspect the freshly issued SCA-User credential and rerun
both payment cases. Record the build, the wallet and ITB verdicts for each session,
and cleanup.

The KB-JWT `aud` binds the proof to the current verifier or DC API origin.
The local SCA interop matrix exercises all four response modes with an issuer
payload that omits `aud` and independently checks the KB-JWT audience, signatures
and hashes. Preserve both the issued bytes and request/proof validation.
Reports retain allowlisted verifier error identifiers or a bounded HTTP status.

## Local timed-key controls

The shared `exerciseTimedNativeSca` fixture uses the production mobile authorizer,
proof builder and platform signing provider. Its timing controls delay or cancel
an operation after native authentication; they do not replace signing or synthesize
successful authentication. It checks two successive payments inside the first
approval's reuse window, rejection after eleven seconds, biometric cancellation,
discarded consent, cancellation after approval, and a fresh successful retry. The
synthetic payment is EUR 12.50 to Synthetic shop. Every successful proof is verified.

On Android, build the normal test APK and explicitly select the physical device:

```sh
adb -s <serial> shell am instrument -w -r \
  -e class 'id.walt.wallet2.mobile.NativeScaDeviceTest#timedPaymentAuthorization' \
  -e wallet.sca timed \
  id.walt.wallet2.mobile.test/androidx.test.runner.AndroidJUnitRunner
```

On iOS, build `assembleWalletCorePhysicalFixturesReleaseXCFramework` with
`-PenableIosBuild=true -PenableWalletSdkPhysicalFixtures=true --max-workers=1`.
Use the existing `waltid-wallet-sdk-ios/PhysicalTests/PhysicalTests.xcodeproj`,
`ProximityPhysicalDeviceTests` scheme, and `ScaPhysicalDeviceTests` test plan on an
entitled physical iPhone. Set `WALLET_SDK_PHYSICAL_FIXTURES=1` for package resolution,
`WALLET_SCA_TIMED=1`, `PHYSICAL_TEAM_ID` and `PHYSICAL_HOST_BUNDLE_ID` for the run.
The ordinary proximity test plan does not select the payment test. Simulator or
missing operator opt-in skips are not physical evidence.

Both hosts display an operator checkpoint before each stage. Approve both initial
prompts; approve EXPIRY and wait without a second prompt; cancel the biometric
prompt at CANCEL_BIOMETRIC; approve CANCEL_AFTER_APPROVAL and RETRY. CANCEL_REVIEW
must not prompt. Keys are disposable and deleted in `finally`.

These are protocol and native authorization checks, not production payment UI,
browser/OS DC API delivery or formal PSD2 qualification. Live ITB remains Android-only.
