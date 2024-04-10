package id.walt.androidSample.app.features.walkthrough

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.walt.crypto.keys.AndroidKey
import id.walt.crypto.keys.KeyType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface WalkthroughViewModel {
    val events: Flow<WalkthroughEvent>

    // Step One
    val keyAlgorithmOptions: List<KeyAlgorithmOption>
    val selectedKeyAlgorithm: StateFlow<KeyAlgorithmOption>

    // Step Two
    val generatedKey: StateFlow<String?>

    fun onKeyAlgorithmSelected(keyAlgorithmOption: KeyAlgorithmOption)
    fun onGenerateKeyClick()
    fun onProgressToStepTwoClick()


    class Fake : WalkthroughViewModel {
        override val events = emptyFlow<WalkthroughEvent>()
        override val keyAlgorithmOptions = KeyAlgorithmOption.all()
        override val selectedKeyAlgorithm = MutableStateFlow(keyAlgorithmOptions.first())
        override val generatedKey = MutableStateFlow("{\"kty\":\"RSA\",\"n\":\"ALzEWJVtxmkmYAeEStt8OSv73SbYL65IRMJ0MjgDt3wwj8KV+0mct3v\\/V3hMjqE2nMJBxj88+vNIRxoRIIzdqU\\/yl7BsV3AVib2qgCw5NybiBxTl3YGbPg4VLt2d5TCHfVpIrMDDUMZaHSlXRilGXLN98pae9IJ1MNuufVnId7iuwosvAMAoNhaD6Webglq88fYHGE0p7M+ISwiWVUjiPhK+YahPwKv5TM+q82dUOZ3eReR7NVCHrglLNOjyxqY7Qc7Kea7klOki0tzbcl7KH2kCfubeKirI4EZujjITaMrHahyAAER91Kv3PYJu2m9eR80IoNg0eKh62+XmlzYpBp8=\",\"e\":\"AQAB\"}")

        override fun onKeyAlgorithmSelected(keyAlgorithmOption: KeyAlgorithmOption) = Unit
        override fun onGenerateKeyClick() = Unit
        override fun onProgressToStepTwoClick() = Unit
    }

    class Default : ViewModel(), WalkthroughViewModel {

        private val _events = Channel<WalkthroughEvent>()
        override val events = _events.receiveAsFlow()

        override val keyAlgorithmOptions = KeyAlgorithmOption.all()
        override val selectedKeyAlgorithm = MutableStateFlow(keyAlgorithmOptions.first())

        override val generatedKey = MutableStateFlow<String?>(null)

        override fun onKeyAlgorithmSelected(keyAlgorithmOption: KeyAlgorithmOption) {
            selectedKeyAlgorithm.update { keyAlgorithmOption }
        }

        // TODO handle case where user does not have lockscreen active
        //  Caused by: java.lang.IllegalStateException: Secure lock screen must be enabled to create keys requiring user authentication
        override fun onGenerateKeyClick() {
            viewModelScope.launch {
                generatedKey.update {
                    when (selectedKeyAlgorithm.value) {
                        KeyAlgorithmOption.RSA -> AndroidKey.generate(KeyType.RSA).getPublicKey().exportJWK()
                        KeyAlgorithmOption.Secp256r1 -> AndroidKey.generate(KeyType.secp256r1).getPublicKey().exportJWK()
                    }
                }
            }
        }

        override fun onProgressToStepTwoClick() {
            viewModelScope.launch { _events.send(WalkthroughEvent.NavigateEvent.NavigateToStepTwo) }
        }

    }
}


sealed interface WalkthroughEvent {
    sealed interface NavigateEvent : WalkthroughEvent {
        data object NavigateToStepTwo : NavigateEvent
    }
}

sealed interface KeyAlgorithmOption {
    data object RSA : KeyAlgorithmOption
    data object Secp256r1 : KeyAlgorithmOption

    companion object {
        fun all(): List<KeyAlgorithmOption> = listOf(RSA, Secp256r1)
    }
}