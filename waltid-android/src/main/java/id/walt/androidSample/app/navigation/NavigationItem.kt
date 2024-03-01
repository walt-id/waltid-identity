package id.walt.androidSample.app.navigation

sealed class NavigationItem(val route: String) {
    data object Main : NavigationItem(Screen.MAIN.name)
    data object Result : NavigationItem(Screen.RESULT.name)
}