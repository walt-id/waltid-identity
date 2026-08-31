package id.walt.walletdemo.compose.logic

import platform.Foundation.NSUserDefaults

fun createIosDemoSharingSettingsStore(appGroupIdentifier: String): DemoSharingSettingsStore {
    require(appGroupIdentifier.isNotEmpty()) {
        "DC API presentation preview is read by the document-provider extension and requires an App Group"
    }
    val defaults = NSUserDefaults(suiteName = appGroupIdentifier)
    return PersistentDemoSharingSettingsStore(
        readEnabled = {
            if (defaults.objectForKey(SHOW_DC_API_PRESENTATION_PREVIEW_KEY) == null) {
                null
            } else {
                defaults.boolForKey(SHOW_DC_API_PRESENTATION_PREVIEW_KEY)
            }
        },
        writeEnabled = { enabled ->
            defaults.setBool(enabled, forKey = SHOW_DC_API_PRESENTATION_PREVIEW_KEY)
        },
        readProximityTransportProfile = {
            defaults.stringForKey(PROXIMITY_TRANSPORT_PROFILE_KEY)
        },
        writeProximityTransportProfile = { profile ->
            defaults.setObject(profile, forKey = PROXIMITY_TRANSPORT_PROFILE_KEY)
        },
    )
}
