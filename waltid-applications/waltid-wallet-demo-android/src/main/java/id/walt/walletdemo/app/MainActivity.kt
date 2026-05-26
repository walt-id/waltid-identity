package id.walt.walletdemo.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import id.walt.walletdemo.app.navigation.Routes
import id.walt.walletdemo.app.navigation.WalletNavGraph
import id.walt.walletdemo.ui.theme.WalletDemoTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val deepLinkFlow = MutableStateFlow(DeepLinkData())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinkFlow.value = parseDeepLink(intent)
        setContent {
            WalletDemoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val deepLink by deepLinkFlow.collectAsState()
                    WalletNavGraph(
                        navController = navController,
                        initialOfferUrl = deepLink.offerUrl,
                        initialVpRequestUrl = deepLink.vpRequestUrl,
                    )
                    androidx.compose.runtime.LaunchedEffect(deepLink) {
                        when {
                            deepLink.offerUrl.isNotEmpty() -> navController.navigate(Routes.RECEIVE) {
                                launchSingleTop = true
                            }
                            deepLink.vpRequestUrl.isNotEmpty() -> navController.navigate(Routes.PRESENT) {
                                launchSingleTop = true
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val data = parseDeepLink(intent)
        if (data.offerUrl.isNotEmpty() || data.vpRequestUrl.isNotEmpty()) {
            deepLinkFlow.value = data
        }
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
