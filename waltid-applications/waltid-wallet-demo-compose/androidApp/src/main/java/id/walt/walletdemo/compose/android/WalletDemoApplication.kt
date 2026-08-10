package id.walt.walletdemo.compose.android

import android.app.Application
import android.util.Log
import id.walt.wallet2.mobile.AndroidDigitalCredentialRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Restores Android Credential Manager registrations without opening the wallet's main UI. */
class WalletDemoApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            val results = AndroidDigitalCredentialRegistry(applicationContext).restorePersistedRegistrations()
            if (results.any { !it.available }) {
                Log.w(TAG, "One or more digital credential registrations could not be restored")
            }
        }
    }

    private companion object {
        const val TAG = "WalletDemoApplication"
    }
}
