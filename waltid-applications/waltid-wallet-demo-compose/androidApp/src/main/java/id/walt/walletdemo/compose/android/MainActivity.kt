package id.walt.walletdemo.compose.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.FragmentActivity
import id.walt.walletdemo.compose.logic.DemoWalletConfig
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoProximityController
import id.walt.walletdemo.compose.logic.DemoReaderTrustSettingsController
import id.walt.walletdemo.compose.logic.createAndroidDemoMobileWallet
import id.walt.walletdemo.compose.logic.createAndroidDemoWallet
import id.walt.walletdemo.compose.logic.createAndroidDemoPinStore
import id.walt.walletdemo.compose.logic.createAndroidDemoSharingSettingsStore
import id.walt.walletdemo.compose.logic.createAndroidDemoReaderTrustSettingsStore
import id.walt.wallet2.mobile.MobileWalletProximityConfiguration
import id.walt.walletdemo.compose.logic.createAndroidDemoBiometricAuthenticator
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtectionMode
import id.walt.walletdemo.compose.ui.MobileWalletDemoApp
import kotlinx.coroutines.launch

const val WALLET_SIGNING_PROTECTION_MODE_EXTRA =
    "id.walt.walletdemo.compose.android.WALLET_SIGNING_PROTECTION_MODE"

class MainActivity : FragmentActivity() {
    private lateinit var controller: WalletDemoController
    private lateinit var proximityController: WalletDemoProximityController
    private lateinit var readerTrustSettingsController: DemoReaderTrustSettingsController
    private lateinit var walletConfig: DemoWalletConfig
    private val onCredentialStoreChanged: () -> Unit = {
        if (::controller.isInitialized) {
            controller.refreshCredentialsFromStore()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        walletConfig = demoWalletConfig().let { config ->
            val override = intent.getStringExtra(WALLET_SIGNING_PROTECTION_MODE_EXTRA)
            if (override == null) config else config.copy(
                signingProtectionMode = WalletDemoSigningProtectionMode.parse(override),
            )
        }
        val wallet = createAndroidDemoWallet(
            context = applicationContext,
            config = walletConfig,
            interactionContextProvider = { this@MainActivity },
        )
        controller = WalletDemoController(
            wallet = wallet,
            pinStore = createAndroidDemoPinStore(applicationContext, walletConfig.walletId),
            biometricAuthenticator = createAndroidDemoBiometricAuthenticator { this@MainActivity },
            signingProtectionMode = walletConfig.signingProtectionMode,
            signingProtectionStore = walletConfig.signingProtectionStore(applicationContext),
            sharingSettings = createAndroidDemoSharingSettingsStore(applicationContext),
        )
        readerTrustSettingsController = DemoReaderTrustSettingsController(
            createAndroidDemoReaderTrustSettingsStore(applicationContext)
        )
        proximityController = WalletDemoProximityController(
            wallet = wallet,
            configurationProvider = {
                readerTrustSettingsController.sessionSnapshot().applyTo(
                    MobileWalletProximityConfiguration()
                )
            },
        )
        WalletDemoCredentialStoreNotifier.addListener(onCredentialStoreChanged)
        handleIntent(intent)

        setContent {
            MobileWalletDemoApp(controller, proximityController, readerTrustSettingsController)
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
        controller.handleApplicationForegrounded()
    }

    override fun onDestroy() {
        WalletDemoCredentialStoreNotifier.removeListener(onCredentialStoreChanged)
        if (::proximityController.isInitialized) proximityController.dismiss()
        super.onDestroy()
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
                    config = walletConfig,
                    interactionContextProvider = { this@MainActivity },
                )
                created.bootstrap(walletConfig.selectedSigningProtection(applicationContext))
                created.wallet.continueAuthorizationIssuance(
                    sessionId = sessionId,
                    callbackUri = callbackUri,
                )
                DigitalCredentialCreateAuthHandoff.clear(this@MainActivity)
                WalletDemoCredentialStoreNotifier.notifyChanged()
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
