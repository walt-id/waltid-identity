package id.walt.androidSample.app.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import id.walt.androidSample.R
import id.walt.androidSample.app.features.walkthrough.StepFiveScreen
import id.walt.androidSample.app.features.walkthrough.StepFourScreen
import id.walt.androidSample.app.features.walkthrough.StepOneScreen
import id.walt.androidSample.app.features.walkthrough.StepThreeScreen
import id.walt.androidSample.app.features.walkthrough.StepTwoScreen
import id.walt.androidSample.app.features.walkthrough.model.WalkthroughEvent
import id.walt.androidSample.app.features.walkthrough.WalkthroughViewModel
import id.walt.androidSample.utils.ObserveAsEvents

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    startDestination: String = NavigationItem.WalkthroughStepOne.route,
) {

    val ctx = LocalContext.current
    val walkthroughViewModel = viewModel<WalkthroughViewModel.Default>()

    ObserveAsEvents(flow = walkthroughViewModel.events) { event ->
        when (event) {
            is WalkthroughEvent.NavigateEvent.ToStepTwo -> navController.navigate(NavigationItem.WalkthroughStepTwo.route)
            WalkthroughEvent.NavigateEvent.ToStepThree -> navController.navigate(NavigationItem.WalkthroughStepThree.route)
            WalkthroughEvent.NavigateEvent.ToStepFour -> navController.navigate(NavigationItem.WalkthroughStepFour.route)
            WalkthroughEvent.NavigateEvent.ToStepFive -> navController.navigate(NavigationItem.WalkthroughStepFive.route)
            WalkthroughEvent.NavigateEvent.GoBack -> navController.popBackStack()
            WalkthroughEvent.NavigateEvent.RestartWalkthrough -> navController.navigate(
                route = NavigationItem.WalkthroughStepOne.route,
                navOptions = navOptions {
                    popUpTo(NavigationItem.WalkthroughStepOne.route) { inclusive = true }
                }
            )

            is WalkthroughEvent.Biometrics -> {}
        }
    }

    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = startDestination
    ) {
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