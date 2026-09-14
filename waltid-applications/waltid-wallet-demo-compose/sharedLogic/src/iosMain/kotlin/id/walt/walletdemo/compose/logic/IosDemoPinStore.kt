package id.walt.walletdemo.compose.logic

import platform.Foundation.NSUserDefaults

fun createIosDemoPinStore(walletId: String): DemoPinStore {
    val defaults = NSUserDefaults.standardUserDefaults
    val recordKey = "$RECORD_KEY_PREFIX$walletId"
    val biometricKey = "$BIOMETRIC_KEY_PREFIX$walletId"
    return PersistentDemoPinStore(
        readRecord = { defaults.stringForKey(recordKey) },
        writeRecord = { record -> defaults.setObject(record, forKey = recordKey) },
        clearRecord = {
            defaults.removeObjectForKey(recordKey)
            defaults.removeObjectForKey(biometricKey)
        },
        readBiometricUnlock = { defaults.boolForKey(biometricKey) },
        writeBiometricUnlock = { enabled -> defaults.setBool(enabled, forKey = biometricKey) },
    )
}

private const val RECORD_KEY_PREFIX = "id.walt.walletdemo.pin."
private const val BIOMETRIC_KEY_PREFIX = "id.walt.walletdemo.pin.biometric."
