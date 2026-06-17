package id.walt.walletdemo.compose.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import id.walt.walletdemo.compose.logic.WalletDemoClientConfig
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.createAndroidWalletDemoClient
import id.walt.walletdemo.compose.ui.WalletDemoApp

class MainActivity : ComponentActivity() {
    private lateinit var controller: WalletDemoController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        controller = WalletDemoController(
            createAndroidWalletDemoClient(
                context = applicationContext,
                config = WalletDemoClientConfig(
                    attestationBaseUrl = BuildConfig.ATTESTATION_BASE_URL,
                    attestationAttesterPath = BuildConfig.ATTESTATION_ATTESTER_PATH,
                    attestationBearerToken = BuildConfig.ATTESTATION_BEARER_TOKEN,
                    attestationHostHeader = BuildConfig.ATTESTATION_HOST_HEADER,
                )
            )
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
