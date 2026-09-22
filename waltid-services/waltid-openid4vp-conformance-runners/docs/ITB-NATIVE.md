# Operator-assisted Android ITB execution

The ordinary JVM runner has a software holder key and cannot supply real TS12 authentication evidence. The optional Android fixture executes the same issuance and reviewed-presentation driver on a physical phone, using a disposable, hardware-backed P-256 key with per-use biometric authentication. The native SCA adapter supplies factors only for an eligible key; successful signing is still required before releasing a proof.

The desktop retains the portal login, organisation API key, case/session correlation, verdict collection and cleanup. Only wallet interactions and the configured reference CA cross the device bridge. Credential storage and private-key operations stay on the phone. The bridge never exposes a generic signing operation.

This is operator-assisted protocol testing, not transaction-consent UI, browser-mediated DC API delivery, formal SCA qualification or an unattended CI mode. The iOS synthetic physical fixture is separate; this live bridge currently supports Android only.

## Run

1. Build and install `:waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:assembleAndroidDeviceTest` with `-PenableAndroidBuild=true`. Record the source revision, any working-tree patch, and the test APK SHA-256. Use a physical phone with strong biometrics enrolled and a screen lock. Explicitly select its ADB serial.
2. Generate a fresh 256-bit token (`openssl rand -hex 32`). Keep it out of reports and shell tracing. Choose an unused device loopback port, for example 39143. Use `adb -s <serial> forward tcp:0 tcp:39143` and retain the returned desktop port.
3. Start the instrumentation in a terminal that remains running:

   ```sh
   adb -s <serial> shell am instrument -w -r \
     -e class id.walt.wallet2.mobile.ItbNativeWalletDeviceTest \
     -e wallet.itb.port 39143 \
     -e wallet.itb.token "$ITB_ANDROID_TOKEN" \
     id.walt.wallet2.mobile.test/androidx.test.runner.AndroidJUnitRunner
   ```

4. Run the normal `itbWallet` task with its existing tenant credentials and these additional environment variables:

   | Variable | Value |
   | --- | --- |
   | `ITB_ANDROID_PORT` | Desktop port returned by ADB forwarding |
   | `ITB_ANDROID_TOKEN` | Same fresh token supplied to instrumentation |
   | `ITB_ANDROID_APK_SHA256` | SHA-256 of the installed test APK |

   Start with `ITB_CASES=ts12_pay_01,ts12_pay_dc_api_01`; the runner includes the issuance prerequisite. Approve the phone's prompts for those synthetic test operations. Each case still has a 120-second limit.
5. On completion, check both the normal ITB reports and instrumentation outcome. Remove the specific ADB forward and unset the temporary token. A normal channel close releases the fixture and deletes the disposable native key in `finally`. If the process is force-killed, normal cleanup cannot be guaranteed; clear only this disposable test application's data before reuse.

The fixture binds only to device loopback and accepts the approved Aegean tenant origin. A fresh token authenticates the coordinator before key creation. Messages are size-bounded and strictly sequenced; disconnect cancels pending work and a broken channel is never retried. Keep the ADB transport restricted to the operator's machine. The token is a local test-channel credential, not an ITB organisation key.

Reports distinguish `JVM_SOFTWARE` from `ANDROID_NATIVE` and include the operator-supplied APK digest. They still require wallet success and correlated terminal ITB `SUCCESS`. All issuer, JOSE, certificate, audience, nonce and verifier-result checks remain strict. A native prompt or successful signature alone is not a passing ITB case.
