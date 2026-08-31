package id.walt.walletdemo.compose.logic

import android.content.Context

fun createAndroidDemoSharingSettingsStore(context: Context): DemoSharingSettingsStore {
    val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    return PersistentDemoSharingSettingsStore(
        readEnabled = {
            if (preferences.contains(SHOW_DC_API_PRESENTATION_PREVIEW_KEY)) {
                preferences.getBoolean(SHOW_DC_API_PRESENTATION_PREVIEW_KEY, true)
            } else {
                null
            }
        },
        writeEnabled = { enabled ->
            check(preferences.edit().putBoolean(SHOW_DC_API_PRESENTATION_PREVIEW_KEY, enabled).commit()) {
                "DC API presentation preview preference could not be persisted"
            }
        },
        readProximityTransportProfile = {
            preferences.getString(PROXIMITY_TRANSPORT_PROFILE_KEY, null)
        },
        writeProximityTransportProfile = { profile ->
            check(preferences.edit().putString(PROXIMITY_TRANSPORT_PROFILE_KEY, profile).commit()) {
                "Proximity transport profile preference could not be persisted"
            }
        },
    )
}

private const val PREFERENCES_NAME = "walt_wallet_demo_settings"
