package id.walt.walletdemo.compose.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.fragment.app.FragmentActivity
import id.walt.walletdemo.compose.logic.DemoWalletConfig
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoProximityController
import id.walt.walletdemo.compose.logic.DemoReaderTrustSettingsController
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtectionMode
import id.walt.walletdemo.compose.ui.MobileWalletDemoApp

const val WALLET_SIGNING_PROTECTION_MODE_EXTRA =
    "id.walt.walletdemo.compose.android.WALLET_SIGNING_PROTECTION_MODE"

class MainActivity : FragmentActivity() {
    private lateinit var activityModel: WalletDemoActivityModel
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
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        walletConfig = demoWalletConfig().let { config ->
            val override = intent.getStringExtra(WALLET_SIGNING_PROTECTION_MODE_EXTRA)
            if (override == null) config else config.copy(
                signingProtectionMode = WalletDemoSigningProtectionMode.parse(override),
            )
        }
        var createdSession = false
        activityModel = ViewModelProvider(this, viewModelFactory {
            initializer {
                createdSession = true
                WalletDemoActivityModel(applicationContext, walletConfig, this@MainActivity)
            }
        })[WalletDemoActivityModel::class.java]
        activityModel.attach(this)
        controller = activityModel.controller
        proximityController = activityModel.proximityController
        readerTrustSettingsController = activityModel.readerTrustSettingsController
        WalletDemoCredentialStoreNotifier.addListener(onCredentialStoreChanged)
        if (createdSession) handleIntent(intent)

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
        if (::activityModel.isInitialized) activityModel.detach(this)
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (DigitalCredentialCreateAuthHandoff.deliver(this, uri)) {
            activityModel.drainOrphanCreateAuthorization()
            return
        }
        controller.handleDeepLink(uri.toString())
    }

}
