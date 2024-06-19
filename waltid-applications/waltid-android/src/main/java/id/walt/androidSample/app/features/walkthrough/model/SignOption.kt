package id.walt.androidSample.app.features.walkthrough.model

sealed interface SignOption {
    data object Raw : SignOption
    data object JWS : SignOption

    companion object {
        fun all() = listOf(Raw, JWS)
    }
}