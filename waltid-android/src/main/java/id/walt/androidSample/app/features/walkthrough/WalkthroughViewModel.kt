package id.walt.androidSample.app.features.walkthrough

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface WalkthroughStep {
    data object One : WalkthroughStep
    data object Two : WalkthroughStep
    data object Three : WalkthroughStep
    data object Four : WalkthroughStep
    data object Five : WalkthroughStep
}

interface WalkthroughViewModel {
    val events: Flow<WalkthroughEvent>

    // Step One
    val keyAlgorithmOptions: List<KeyAlgorithmOption>
    val selectedKeyAlgorithm: StateFlow<KeyAlgorithmOption>
    val generatedKey: StateFlow<String?>

    // Step Two
    val publicKey: StateFlow<String?>

    // Step Three
    val methodOptions: List<MethodOption>
    val selectedMethod: StateFlow<MethodOption>
    val did: StateFlow<String?>

    // Step Four
    val plainText: StateFlow<String>
    val signOptions: List<SignOption>
    val selectedSignOption: StateFlow<SignOption>
    val signedOutput: StateFlow<String?>

    // Step Five
    val verifiedText: StateFlow<String?>

    fun onKeyAlgorithmSelected(keyAlgorithmOption: KeyAlgorithmOption)
    fun onGenerateKeyClick()
    fun onRetrievePublicKeyClick()
    fun onGenerateDIDClick()
    fun onSignTextClick()
    fun onVerifyClick()
    fun onNextStepClick()


    class Fake : WalkthroughViewModel {
        override val events = emptyFlow<WalkthroughEvent>()
        override val keyAlgorithmOptions = KeyAlgorithmOption.all()
        override val selectedKeyAlgorithm = MutableStateFlow(KeyAlgorithmOption.RSA)
        override val generatedKey = MutableStateFlow("{\"kty\":\"RSA\",\"n\":\"ALzEWJVtxmkmYAeEStt8OSv73SbYL65IRMJ0MjgDt3wwj8KV+0mct3v\\/V3hMjqE2nMJBxj88+vNIRxoRIIzdqU\\/yl7BsV3AVib2qgCw5NybiBxTl3YGbPg4VLt2d5TCHfVpIrMDDUMZaHSlXRilGXLN98pae9IJ1MNuufVnId7iuwosvAMAoNhaD6Webglq88fYHGE0p7M+ISwiWVUjiPhK+YahPwKv5TM+q82dUOZ3eReR7NVCHrglLNOjyxqY7Qc7Kea7klOki0tzbcl7KH2kCfubeKirI4EZujjITaMrHahyAAER91Kv3PYJu2m9eR80IoNg0eKh62+XmlzYpBp8=\",\"e\":\"AQAB\"}")
        override val publicKey = MutableStateFlow("")
        override val methodOptions = MethodOption.all()
        override val selectedMethod = MutableStateFlow(MethodOption.Key)
        override val did = MutableStateFlow("did:example:123456789abcdefghi")
        override val plainText = MutableStateFlow("")
        override val signOptions = SignOption.all()
        override val selectedSignOption = MutableStateFlow(SignOption.Plain)
        override val signedOutput = MutableStateFlow("")
        override val verifiedText = MutableStateFlow("")

        override fun onKeyAlgorithmSelected(keyAlgorithmOption: KeyAlgorithmOption) = Unit
        override fun onGenerateKeyClick() = Unit
        override fun onRetrievePublicKeyClick() = Unit
        override fun onGenerateDIDClick() = Unit
        override fun onSignTextClick() = Unit
        override fun onVerifyClick() = Unit
        override fun onNextStepClick() = Unit
    }

    class Default : ViewModel(), WalkthroughViewModel {

        private val _events = Channel<WalkthroughEvent>()
        override val events = _events.receiveAsFlow()

        override val keyAlgorithmOptions = KeyAlgorithmOption.all()
        override val selectedKeyAlgorithm = MutableStateFlow<KeyAlgorithmOption>(KeyAlgorithmOption.RSA)
        override val generatedKey = MutableStateFlow<String?>(null)
        override val publicKey = MutableStateFlow<String?>(null)
        override val methodOptions = MethodOption.all()
        override val selectedMethod = MutableStateFlow<MethodOption>(MethodOption.Key)
        override val did = MutableStateFlow<String?>(null)
        override val plainText = MutableStateFlow("")
        override val signOptions = SignOption.all()
        override val selectedSignOption = MutableStateFlow<SignOption>(SignOption.Plain)
        override val signedOutput = MutableStateFlow<String?>(null)
        override val verifiedText = MutableStateFlow<String?>(null)

        override fun onKeyAlgorithmSelected(keyAlgorithmOption: KeyAlgorithmOption) {
            selectedKeyAlgorithm.update { keyAlgorithmOption }
        }

        // TODO handle case where user does not have lockscreen active
        //  Caused by: java.lang.IllegalStateException: Secure lock screen must be enabled to create keys requiring user authentication
        override fun onGenerateKeyClick() {
            viewModelScope.launch {
//                generatedKey.update {
//                    when (selectedKeyAlgorithm.value) {
//                        KeyAlgorithmOption.RSA -> AndroidKey.generate(KeyType.RSA).getPublicKey().exportJWK()
//                        KeyAlgorithmOption.Secp256r1 -> AndroidKey.generate(KeyType.secp256r1).getPublicKey().exportJWK()
//                    }
//                }
            }
        }

        override fun onRetrievePublicKeyClick() {
            TODO("Not yet implemented")
        }

        override fun onGenerateDIDClick() {
            TODO("Not yet implemented")
        }

        override fun onSignTextClick() {
            TODO("Not yet implemented")
        }

        override fun onVerifyClick() {
            TODO("Not yet implemented")
        }

        override fun onNextStepClick() {
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