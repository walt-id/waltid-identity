package id.walt.androidSample.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import id.walt.androidSample.app.features.main.MainViewModel
import id.walt.androidSample.app.features.main.MainUi
import id.walt.androidSample.app.features.result.ResultUi

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    startDestination: String = NavigationItem.Main.route,
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = startDestination
    ) {
        val sharedViewModel = MainViewModel.Default()
        composable(NavigationItem.Main.route) {
            MainUi(sharedViewModel, navController)
        }
        composable(NavigationItem.Result.route) {
            ResultUi(sharedViewModel, navController)
        }
    }
}