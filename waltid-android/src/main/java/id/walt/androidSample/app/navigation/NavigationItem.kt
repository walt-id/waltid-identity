package id.walt.androidSample.app.navigation

sealed class NavigationItem(val route: String) {
    data object Main : NavigationItem("Main")
    data object Result : NavigationItem("Result")
    data object WalkthroughStepOne : NavigationItem("WalkthroughStepOne")
    data object WalkthroughStepTwo : NavigationItem("WalkthroughStepTwo")
    data object WalkthroughStepThree : NavigationItem("WalkthroughStepThree")
    data object WalkthroughStepFour : NavigationItem("WalkthroughStepFour")
    data object WalkthroughStepFive : NavigationItem("WalkthroughStepFive")
}