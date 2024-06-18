package id.walt.androidSample.app.features.walkthrough.model

sealed interface MethodOption {
    data object Key : MethodOption
    data object JWK : MethodOption

    companion object {
        fun all() = listOf(Key, JWK)
    }
}