package id.walt.androidSample.app.features.walkthrough.model

sealed interface WalkthroughEvent {
    sealed interface NavigateEvent : WalkthroughEvent {
        data object ToStepTwo : NavigateEvent
        data object ToStepThree : NavigateEvent
        data object ToStepFour : NavigateEvent
        data object ToStepFive : NavigateEvent
        data object GoBack : NavigateEvent
        data object RestartWalkthrough : NavigateEvent
    }
    sealed interface Biometrics : WalkthroughEvent {
        data object BiometricsUnavailable : Biometrics
        data object BiometricAuthenticationFailure : Biometrics
        data class BiometricError(val msg: String) : Biometrics
        data object SecureLockScreenNotEnabled: Biometrics
    }
}