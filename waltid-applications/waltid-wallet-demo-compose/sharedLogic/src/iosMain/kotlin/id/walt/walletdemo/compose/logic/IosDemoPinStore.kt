package id.walt.walletdemo.compose.logic

import platform.Foundation.NSUserDefaults

fun createIosDemoPinStore(walletId: String): DemoPinStore {
    val defaults = NSUserDefaults.standardUserDefaults
    val recordKey = "$RECORD_KEY_PREFIX$walletId"
    return PersistentDemoPinStore(
        readRecord = { defaults.stringForKey(recordKey) },
        writeRecord = { record -> defaults.setObject(record, forKey = recordKey) },
    )
}

private const val RECORD_KEY_PREFIX = "id.walt.walletdemo.pin."
