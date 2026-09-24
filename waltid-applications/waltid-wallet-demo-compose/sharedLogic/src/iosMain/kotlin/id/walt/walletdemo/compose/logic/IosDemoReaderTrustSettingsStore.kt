package id.walt.walletdemo.compose.logic

import platform.Foundation.NSUserDefaults

fun createIosDemoReaderTrustSettingsStore(appGroupIdentifier: String): DemoReaderTrustSettingsStore {
    require(appGroupIdentifier.isNotEmpty()) { "Reader trust settings require an App Group" }
    val defaults = NSUserDefaults(suiteName = appGroupIdentifier)
    return PersistentDemoReaderTrustSettingsStore(
        read = { defaults.stringForKey(READER_TRUST_SETTINGS_KEY) },
        write = { encoded -> defaults.setObject(encoded, forKey = READER_TRUST_SETTINGS_KEY) },
    )
}
