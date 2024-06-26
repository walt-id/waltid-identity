package id.walt.androidSample.app.features.walkthrough.model

sealed interface KeyAlgorithmOption {
    data object RSA : KeyAlgorithmOption
    data object Secp256r1 : KeyAlgorithmOption

    companion object {
        fun all(): List<KeyAlgorithmOption> = listOf(RSA, Secp256r1)
    }
}