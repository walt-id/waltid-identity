package id.walt.walletdemo.compose.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.createAndroidDemoMobileWallet
import id.walt.walletdemo.compose.logic.createAndroidDemoWallet
import id.walt.walletdemo.compose.logic.createAndroidDemoPinStore
import id.walt.walletdemo.compose.ui.WalletDemoApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var controller: WalletDemoController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        val config = demoWalletConfig()
        controller = WalletDemoController(
            wallet = createAndroidDemoWallet(
                context = applicationContext,
                config = config,
            ),
            pinStore = createAndroidDemoPinStore(applicationContext, config.walletId),
        )
        handleIntent(intent)

        setContent {
            WalletDemoApp(controller)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (!::controller.isInitialized) return
        // CreateActivity stores into the shared DB; reload so Credentials tab does not stay stale.
        controller.refreshCredentialsFromStore()
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (DigitalCredentialCreateAuthHandoff.deliver(this, uri)) {
            drainOrphanCreateAuthorization()
            return
        }
        controller.handleDeepLink(uri.toString())
    }

    /**
     * Completes CREATE_CREDENTIAL authorization-code issuance when the create Activity was
     * destroyed before the `openid://` callback returned. The credential is stored; the
     * Credential Manager create result may already be lost.
     */
    private fun drainOrphanCreateAuthorization() {
        val orphan = OrphanAuthorizationCallback.take() ?: return
        val (sessionId, callbackUri) = orphan
        lifecycleScope.launch {
            runCatching {
                val created = createAndroidDemoMobileWallet(
                    context = applicationContext,
                    config = demoWalletConfig(),
                )
                created.wallet.bootstrap()
                created.wallet.continueAuthorizationIssuance(
                    sessionId = sessionId,
                    callbackUri = callbackUri,
                )
                DigitalCredentialCreateAuthHandoff.clear(this@MainActivity)
                controller.refreshCredentialsFromStore()
                Log.i(TAG, "Completed orphan CREATE authorization for session=$sessionId")
            }.onFailure { error ->
                Log.e(TAG, "Orphan CREATE authorization failed", error)
            }
        }
    }

    private companion object {
        private const val TAG = "WaltDigitalCredentials"
    }
}
