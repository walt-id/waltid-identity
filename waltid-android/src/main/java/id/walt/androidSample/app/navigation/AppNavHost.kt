package id.walt.androidSample.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import id.walt.androidSample.app.features.main.MainViewModel
import id.walt.androidSample.app.features.result.ResultUi
import id.walt.androidSample.app.features.walkthrough.StepFiveScreen
import id.walt.androidSample.app.features.walkthrough.StepFourScreen
import id.walt.androidSample.app.features.walkthrough.StepOneScreen
import id.walt.androidSample.app.features.walkthrough.StepThreeScreen
import id.walt.androidSample.app.features.walkthrough.StepTwoScreen
import id.walt.androidSample.app.features.walkthrough.WalkthroughViewModel

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    startDestination: String = NavigationItem.Main.route,
) {

    val walkthroughViewModel = viewModel<WalkthroughViewModel.Default>()

    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = startDestination
    ) {
        val sharedViewModel = MainViewModel.Default()
        composable(NavigationItem.Main.route) {
           StepOneScreen(viewModel = walkthroughViewModel, navController = navController)
        }
        composable(NavigationItem.Result.route) {
            ResultUi(sharedViewModel, navController)
        }
        composable(NavigationItem.WalkthroughStepOne.route) {
            StepOneScreen(viewModel = walkthroughViewModel, navController = navController)
        }
        composable(NavigationItem.WalkthroughStepTwo.route) {
            StepTwoScreen(viewModel = walkthroughViewModel, navController = navController)
        }
        composable(NavigationItem.WalkthroughStepThree.route) {
            StepThreeScreen(viewModel = walkthroughViewModel, navController = navController)
        }
        composable(NavigationItem.WalkthroughStepFour.route) {
            StepFourScreen(viewModel = walkthroughViewModel, navController = navController)
        }
        composable(NavigationItem.WalkthroughStepFive.route) {
            StepFiveScreen(viewModel = walkthroughViewModel, navController = navController)
        }
    }
}