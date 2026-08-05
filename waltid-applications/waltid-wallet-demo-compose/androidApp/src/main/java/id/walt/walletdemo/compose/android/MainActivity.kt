package id.walt.walletdemo.compose.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import id.walt.walletdemo.compose.logic.DemoWalletConfig
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.createAndroidDemoWallet
import id.walt.walletdemo.compose.logic.createAndroidDemoPinStore
import id.walt.walletdemo.compose.ui.WalletDemoApp

const val WALLET_BIOMETRIC_ENABLED_EXTRA = "id.walt.walletdemo.compose.android.WALLET_BIOMETRIC_ENABLED"

class MainActivity : FragmentActivity() {
    private lateinit var controller: WalletDemoController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        val config = DemoWalletConfig(
            attestationBaseUrl = BuildConfig.ATTESTATION_BASE_URL,
            attestationAttesterPath = BuildConfig.ATTESTATION_ATTESTER_PATH,
            attestationBearerToken = BuildConfig.ATTESTATION_BEARER_TOKEN,
            attestationHostHeader = BuildConfig.ATTESTATION_HOST_HEADER,
            transactionDataProfilesUrl = BuildConfig.TRANSACTION_DATA_PROFILES_URL,
            biometricEnabled = intent.getBooleanExtra(
                WALLET_BIOMETRIC_ENABLED_EXTRA,
                BuildConfig.WALLET_BIOMETRIC_ENABLED,
            ),
        )
        controller = WalletDemoController(
            wallet = createAndroidDemoWallet(
                context = applicationContext,
                config = config,
                interactionContextProvider = { this@MainActivity },
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

    private fun handleIntent(intent: Intent?) {
        intent?.data?.toString()?.let(controller::handleDeepLink)
    }
}
