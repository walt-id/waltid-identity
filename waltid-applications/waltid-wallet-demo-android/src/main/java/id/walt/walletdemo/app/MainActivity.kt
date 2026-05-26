package id.walt.walletdemo.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import id.walt.walletdemo.app.navigation.Routes
import id.walt.walletdemo.app.navigation.WalletNavGraph
import id.walt.walletdemo.ui.theme.WalletDemoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deepLink = parseDeepLink(intent)
        setContent {
            WalletDemoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    WalletNavGraph(
                        navController = navController,
                        initialOfferUrl = deepLink.offerUrl,
                        initialVpRequestUrl = deepLink.vpRequestUrl,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun parseDeepLink(intent: Intent?): DeepLinkData {
        val uri = intent?.data ?: return DeepLinkData()
        val fullUrl = uri.toString()
        return when (uri.scheme) {
            "openid-credential-offer" -> DeepLinkData(offerUrl = fullUrl)
            "openid4vp" -> DeepLinkData(vpRequestUrl = fullUrl)
            else -> DeepLinkData()
        }
    }
}

private data class DeepLinkData(
    val offerUrl: String = "",
    val vpRequestUrl: String = "",
)
