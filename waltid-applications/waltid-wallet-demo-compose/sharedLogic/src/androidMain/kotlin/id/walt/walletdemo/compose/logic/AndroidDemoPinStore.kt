package id.walt.walletdemo.compose.logic

import android.content.Context

fun createAndroidDemoPinStore(
    context: Context,
    walletId: String,
): DemoPinStore {
    val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    val recordKey = "$RECORD_KEY_PREFIX$walletId"
    return PersistentDemoPinStore(
        readRecord = { preferences.getString(recordKey, null) },
        writeRecord = { record ->
            val editor = preferences.edit()
            if (record == null) editor.remove(recordKey) else editor.putString(recordKey, record)
            check(editor.commit()) {
                "PIN verifier could not be persisted"
            }
        },
    )
}

private const val PREFERENCES_NAME = "walt_wallet_demo_pin_verifiers"
private const val RECORD_KEY_PREFIX = "pin."
