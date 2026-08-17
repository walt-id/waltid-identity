package id.walt.walletdemo.compose.android

import id.walt.walletdemo.compose.logic.DemoWalletConfig

/**
 * The demo app's build-time wallet configuration, shared by every Android entry point.
 *
 * The Credential Manager provider activity is launched by the operating system independently of
 * [MainActivity], so both must read the same values or they open different wallet databases and apply
 * different transaction-data policy.
 */
internal fun demoWalletConfig(): DemoWalletConfig = DemoWalletConfig(
    attestationBaseUrl = BuildConfig.ATTESTATION_BASE_URL,
    attestationAttesterPath = BuildConfig.ATTESTATION_ATTESTER_PATH,
    attestationBearerToken = BuildConfig.ATTESTATION_BEARER_TOKEN,
    attestationHostHeader = BuildConfig.ATTESTATION_HOST_HEADER,
    transactionDataProfilesUrl = BuildConfig.TRANSACTION_DATA_PROFILES_URL,
    biometricEnabled = BuildConfig.WALLET_BIOMETRIC_ENABLED,
)
