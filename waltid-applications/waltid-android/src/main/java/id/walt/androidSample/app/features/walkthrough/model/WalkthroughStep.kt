package id.walt.androidSample.app.features.walkthrough.model

sealed interface WalkthroughStep {
    data object One : WalkthroughStep
    data object Two : WalkthroughStep
    data object Three : WalkthroughStep
    data object Four : WalkthroughStep
    data object Five : WalkthroughStep
}