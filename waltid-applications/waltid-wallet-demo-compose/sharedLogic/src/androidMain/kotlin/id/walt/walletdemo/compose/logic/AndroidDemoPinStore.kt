package id.walt.walletdemo.compose.logic

import android.content.Context
import android.content.SharedPreferences
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.jdk.JDK
import org.bouncycastle.jce.provider.BouncyCastleProvider

fun createAndroidDemoPinStore(
    context: Context,
    walletId: String,
): DemoPinStore {
    val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    return createAndroidDemoPinStore(preferences, walletId)
}

internal fun createAndroidDemoPinStore(preferences: SharedPreferences, walletId: String): DemoPinStore {
    val recordKey = "$RECORD_KEY_PREFIX$walletId"
    val biometricKey = "$BIOMETRIC_KEY_PREFIX$walletId"
    val pendingKey = "biometric.pending.$walletId"
    return PersistentDemoPinStore(
        readRecord = { preferences.getString(recordKey, null) },
        writeRecord = { record ->
            synchronized(preferences) {
                val previous = preferences.getString(recordKey, null)
                preferences.commitAccessChange("PIN verifier could not be persisted",
                    change = { putString(recordKey, record) }, restore = { putString(recordKey, previous) })
            }
        },
        clearRecord = {
            check(preferences.edit().remove(recordKey).remove(biometricKey).remove(pendingKey).commit()) {
                "PIN verifier could not be cleared"
            }
        },
        readBiometricUnlock = { preferences.getBoolean(biometricKey, false) },
        writeBiometricUnlock = { enabled ->
            synchronized(preferences) {
                val previous = preferences.getBoolean(biometricKey, false)
                preferences.commitAccessChange("Biometric unlock preference could not be persisted",
                    change = { putBoolean(biometricKey, enabled) }, restore = { putBoolean(biometricKey, previous) })
            }
        },
        readBiometricSetupPending = { preferences.getBoolean(pendingKey, false) },
        writeBiometricSetupPending = { pending ->
            synchronized(preferences) {
                val previous = preferences.getBoolean(pendingKey, false)
                preferences.commitAccessChange("Biometric setup choice could not be persisted",
                    change = { putBoolean(pendingKey, pending) }, restore = { putBoolean(pendingKey, previous) })
            }
        },
        provider = androidPinCryptographyProvider,
    )
}

// SharedPreferences updates memory before commit reports a disk failure. Restore the old
// value synchronously so a failed replacement cannot become the active in-memory verifier.
private fun SharedPreferences.commitAccessChange(
    message: String,
    change: SharedPreferences.Editor.() -> Unit,
    restore: SharedPreferences.Editor.() -> Unit,
) {
    if (edit().apply(change).commit()) return
    edit().apply(restore).commit()
    error(message)
}

private val androidPinCryptographyProvider by lazy {
    CryptographyProvider.JDK(BouncyCastleProvider())
}

private const val PREFERENCES_NAME = "walt_wallet_demo_pin_verifiers"
private const val RECORD_KEY_PREFIX = "pin."
private const val BIOMETRIC_KEY_PREFIX = "biometric."
