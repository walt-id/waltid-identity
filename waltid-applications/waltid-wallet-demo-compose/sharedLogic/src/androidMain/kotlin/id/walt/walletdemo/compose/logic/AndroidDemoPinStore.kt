package id.walt.walletdemo.compose.logic

import android.content.Context

fun createAndroidDemoPinStore(
    context: Context,
    walletId: String,
): DemoPinStore {
    val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    val recordKey = "$RECORD_KEY_PREFIX$walletId"
    val biometricKey = "$BIOMETRIC_KEY_PREFIX$walletId"
    return PersistentDemoPinStore(
        readRecord = { preferences.getString(recordKey, null) },
        writeRecord = { record ->
            check(preferences.edit().putString(recordKey, record).commit()) {
                "PIN verifier could not be persisted"
            }
        },
        clearRecord = {
            check(preferences.edit().remove(recordKey).remove(biometricKey).commit()) {
                "PIN verifier could not be cleared"
            }
        },
        readBiometricUnlock = { preferences.getBoolean(biometricKey, false) },
        writeBiometricUnlock = { enabled ->
            check(preferences.edit().putBoolean(biometricKey, enabled).commit()) {
                "Biometric unlock preference could not be persisted"
            }
        },
    )
}

private const val PREFERENCES_NAME = "walt_wallet_demo_pin_verifiers"
private const val RECORD_KEY_PREFIX = "pin."
private const val BIOMETRIC_KEY_PREFIX = "biometric."
