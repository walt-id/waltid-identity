package id.walt.walletdemo.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import id.walt.walletdemo.ui.screens.HomeScreen
import id.walt.walletdemo.ui.screens.PresentScreen
import id.walt.walletdemo.ui.screens.ReceiveScreen
import id.walt.walletdemo.ui.screens.SettingsScreen
import id.walt.walletdemo.viewmodel.WalletViewModel

object Routes {
    const val HOME = "home"
    const val RECEIVE = "receive"
    const val PRESENT = "present"
    const val SETTINGS = "settings"
}

@Composable
fun WalletNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    initialOfferUrl: String = "",
    initialVpRequestUrl: String = "",
) {
    val viewModel: WalletViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToReceive = { navController.navigate(Routes.RECEIVE) },
                onNavigateToPresent = { navController.navigate(Routes.PRESENT) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.RECEIVE,
            deepLinks = listOf(
                navDeepLink { uriPattern = "openid-credential-offer://{path}" },
            ),
        ) { backStackEntry ->
            val deepLinkUrl = backStackEntry.arguments?.getString("path")?.let {
                "openid-credential-offer://$it"
            } ?: initialOfferUrl
            ReceiveScreen(
                viewModel = viewModel,
                initialOfferUrl = deepLinkUrl,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.PRESENT,
            deepLinks = listOf(
                navDeepLink { uriPattern = "openid4vp://{path}" },
            ),
        ) { backStackEntry ->
            val deepLinkUrl = backStackEntry.arguments?.getString("path")?.let {
                "openid4vp://$it"
            } ?: initialVpRequestUrl
            PresentScreen(
                viewModel = viewModel,
                initialRequestUrl = deepLinkUrl,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
