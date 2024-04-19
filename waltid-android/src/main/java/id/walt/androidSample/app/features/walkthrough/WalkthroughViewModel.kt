package id.walt.androidSample.app.features.walkthrough

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.walt.androidSample.app.features.walkthrough.model.KeyAlgorithmOption
import id.walt.androidSample.app.features.walkthrough.model.MethodOption
import id.walt.androidSample.app.features.walkthrough.model.SignOption
import id.walt.androidSample.app.features.walkthrough.model.VerificationResult
import id.walt.androidSample.app.features.walkthrough.model.WalkthroughEvent
import id.walt.crypto.keys.AndroidKey
import id.walt.crypto.keys.KeyType
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.DidResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.InvalidAlgorithmParameterException

interface WalkthroughViewModel {
    val events: SharedFlow<WalkthroughEvent>

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
    val verificationResult: StateFlow<VerificationResult?>

    fun onKeyAlgorithmSelected(keyAlgorithmOption: KeyAlgorithmOption)
    fun onMethodOptionSelected(methodOption: MethodOption)
    fun onSignOptionSelected(signOption: SignOption)
    fun onPlainTextChanged(plainText: String)
    fun onGenerateKeyClick()
    fun onRetrievePublicKeyClick()
    fun onGenerateDIDClick()
    fun onSignTextClick()
    fun onVerifyClick()
    fun onBackClick()
    fun onGoToStepTwoClick()
    fun onGoToStepThreeClick()
    fun onGoToStepFourClick()
    fun onGoToStepFiveClick()
    fun onCompleteWalkthroughClick()
    fun onBiometricsAuthFailure(msg: String? = null)
    fun onBiometricsUnavailable()


    class Fake : WalkthroughViewModel {
        override val events = MutableSharedFlow<WalkthroughEvent>()
        override val keyAlgorithmOptions = KeyAlgorithmOption.all()
        override val selectedKeyAlgorithm = MutableStateFlow(KeyAlgorithmOption.RSA)
        override val generatedKey = MutableStateFlow<String?>(null)
        override val publicKey =
            MutableStateFlow("{\"kty\":\"RSA\",\"n\":\"ALzEWJVtxmkmYAeEStt8OSv73SbYL65IRMJ0MjgDt3wwj8KV+0mct3v\\/V3hMjqE2nMJBxj88+vNIRxoRIIzdqU\\/yl7BsV3AVib2qgCw5NybiBxTl3YGbPg4VLt2d5TCHfVpIrMDDUMZaHSlXRilGXLN98pae9IJ1MNuufVnId7iuwosvAMAoNhaD6Webglq88fYHGE0p7M+ISwiWVUjiPhK+YahPwKv5TM+q82dUOZ3eReR7NVCHrglLNOjyxqY7Qc7Kea7klOki0tzbcl7KH2kCfubeKirI4EZujjITaMrHahyAAER91Kv3PYJu2m9eR80IoNg0eKh62+XmlzYpBp8=\",\"e\":\"AQAB\"}")
        override val methodOptions = MethodOption.all()
        override val selectedMethod = MutableStateFlow(MethodOption.Key)
        override val did =
            MutableStateFlow("did:jwk:eyJrdHkiOiJSU0EiLCJuIjoiQUpkMGFkOG54QkNoSG1KendLUndXSWRGVTE4ZGlHa1E4Y0s5aGVTeXg5RWtnSjE3S2xLK2dueUUyaWF0cDBNT01DUVA5Y1NLcUFkbnRtWTJPOW82MEtnWGswbmxTaUJEVVJsVUZ6aUxpNFhwbXhFVFdzYkhUU00xVU1YTVEraFwvOXBDNE10MGZQd09Vc2ZBNElZbTFIaXExWUNUeDQ4MzFmNFdScDVrbVlReG14YVV1UkxwcnZKS0lWVXlnRWhsRzh4bU1hTDdob2YrWkc3XC9QT21rOTNWZnlzSFFcL25SdzhOaE14NFJvT1lCeHVIXC9zRytKSlJiZzR1dzhkRTlKbmpIMGl2RFJHNHZpUjBURUxnb245R1wvOVwvRk1pRFZaelRLTFhGdThEaHNscjZacDI2bUhKenFxU1FUZWlZVjNNMGpGRmpNXC9aSUMzSUhqZW4rZTYrTTZiN009IiwiZSI6IkFRQUIifQ")
        override val plainText = MutableStateFlow("")
        override val signOptions = SignOption.all()
        override val selectedSignOption = MutableStateFlow(SignOption.Raw)
        override val signedOutput = MutableStateFlow("QmhjUDl3NExjeGNOUUQ3U09iRzFYT2NPOEc5bHREQ3ZvYW9jWmdoSjZNRjl0dnpFcDBjZ1crTFNR")
        override val verificationResult = MutableStateFlow(VerificationResult.Success)

        override fun onKeyAlgorithmSelected(keyAlgorithmOption: KeyAlgorithmOption) = Unit
        override fun onMethodOptionSelected(methodOption: MethodOption) = Unit
        override fun onSignOptionSelected(signOption: SignOption) = Unit
        override fun onPlainTextChanged(plainText: String) = Unit
        override fun onGenerateKeyClick() = Unit
        override fun onRetrievePublicKeyClick() = Unit
        override fun onGenerateDIDClick() = Unit
        override fun onSignTextClick() = Unit
        override fun onVerifyClick() = Unit
        override fun onGoToStepTwoClick() = Unit
        override fun onGoToStepThreeClick() = Unit
        override fun onGoToStepFourClick() = Unit
        override fun onGoToStepFiveClick() = Unit
        override fun onBackClick() = Unit
        override fun onCompleteWalkthroughClick() = Unit
        override fun onBiometricsAuthFailure(msg: String?) = Unit
        override fun onBiometricsUnavailable() = Unit
    }

    class Default : ViewModel(), WalkthroughViewModel {

        private val _events = Channel<WalkthroughEvent>()
        override val events = _events.receiveAsFlow().shareIn(viewModelScope, started = SharingStarted.WhileSubscribed(5_000L))

        override val keyAlgorithmOptions = KeyAlgorithmOption.all()
        override val selectedKeyAlgorithm = MutableStateFlow<KeyAlgorithmOption>(KeyAlgorithmOption.RSA)

        private var key: AndroidKey? = null
        override val generatedKey = MutableStateFlow<String?>(null)
        override val publicKey = MutableStateFlow<String?>(null)

        override val methodOptions = MethodOption.all()
        override val selectedMethod = MutableStateFlow<MethodOption>(MethodOption.Key)

        private var didResult: DidResult? = null
        override val did = MutableStateFlow<String?>(null)

        override val plainText = MutableStateFlow("")

        override val signOptions = SignOption.all()
        override val selectedSignOption = MutableStateFlow<SignOption>(SignOption.Raw)
        private var signedOutputByteArray: ByteArray? = null
        private var signedOutputJWS: String? = null
        override val signedOutput = MutableStateFlow<String?>(null)

        override val verificationResult = MutableStateFlow<VerificationResult?>(null)

        override fun onKeyAlgorithmSelected(keyAlgorithmOption: KeyAlgorithmOption) {
            selectedKeyAlgorithm.update { currentAlgorithm ->
                if (currentAlgorithm != keyAlgorithmOption) {
                    resetKey()
                    resetDid()
                    resetSignedResult()
                }

                keyAlgorithmOption
            }
        }

        override fun onMethodOptionSelected(methodOption: MethodOption) {
            selectedMethod.update { currentMethod ->
                if (currentMethod != methodOption) {
                    resetDid()
                    resetSignedResult()
                }

                methodOption
            }
        }

        override fun onSignOptionSelected(signOption: SignOption) {
            selectedSignOption.update { currentSignOption ->
                if (currentSignOption != signOption) {
                    resetSignedResult()
                }

                signOption
            }
        }

        override fun onPlainTextChanged(plainText: String) {
            this.plainText.update { plainText }
        }

        override fun onGenerateKeyClick() {
            viewModelScope.launch {
                try {
                    val androidKey = when (selectedKeyAlgorithm.value) {
                        KeyAlgorithmOption.RSA -> AndroidKey.generate(KeyType.RSA)
                        KeyAlgorithmOption.Secp256r1 -> AndroidKey.generate(KeyType.secp256r1)
                    }
                    key = androidKey
                    generatedKey.update { androidKey.exportJWK() }
                } catch (e: InvalidAlgorithmParameterException) {
                    println("Error generating key: ${e.message}")
                    _events.send(WalkthroughEvent.Biometrics.SecureLockScreenNotEnabled)
                }
            }
        }

        // TODO provide other options to export public key
        override fun onRetrievePublicKeyClick() {
            key?.let { androidKey ->
                viewModelScope.launch {
                    publicKey.update { androidKey.getPublicKey().exportJWK() }
                }
            }
        }

        override fun onGenerateDIDClick() {
            viewModelScope.launch {
                key?.let { androidKey ->
                    DidService.minimalInit()
                    val result = when (selectedMethod.value) {
                        MethodOption.Key -> DidService.registerByKey("key", androidKey)
                        MethodOption.JWK -> DidService.registerByKey("jwk", androidKey)
                    }
                    didResult = result
                    did.update { result.did }
                }
            }
        }

        override fun onSignTextClick() {
            viewModelScope.launch {
                key?.let { androidKey ->
                    signedOutput.update {
                        when (selectedSignOption.value) {
                            SignOption.JWS -> {
                                val signedOutput = androidKey.signJws(
                                    plaintext = plainText.value.toByteArray(),
                                    headers = mapOf("kid" to androidKey.getKeyId())
                                )
                                signedOutputJWS = signedOutput
                                signedOutput
                            }
                            SignOption.Raw -> {
                                val signedByteArray = androidKey.signRaw(plainText.value.toByteArray())
                                signedOutputByteArray = signedByteArray
                                Base64.encodeToString(signedByteArray, Base64.DEFAULT)
                            }
                        }
                    }
                }
            }
        }

        override fun onVerifyClick() {
            viewModelScope.launch {
                key?.let { androidKey ->
                    when (selectedSignOption.value) {
                        SignOption.Raw -> {
                            signedOutputByteArray?.let { byteArrayToVerify ->
                                val result = androidKey.verifyRaw(byteArrayToVerify, plainText.value.toByteArray())
                                if (result.isSuccess) {
                                    verificationResult.update { VerificationResult.Success }
                                } else {
                                    verificationResult.update { VerificationResult.Failed }
                                }
                            }
                        }

                        SignOption.JWS -> {
                            signedOutputJWS?.let { signedJWSToVerify ->
                                val result = androidKey.verifyJws(signedJWSToVerify)
                                if (result.isSuccess) {
                                    verificationResult.update { VerificationResult.Success }
                                } else {
                                    verificationResult.update { VerificationResult.Failed }
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun onGoToStepTwoClick() {
            viewModelScope.launch { _events.send(WalkthroughEvent.NavigateEvent.ToStepTwo) }
        }

        override fun onGoToStepThreeClick() {
            viewModelScope.launch { _events.send(WalkthroughEvent.NavigateEvent.ToStepThree) }
        }

        override fun onGoToStepFourClick() {
            viewModelScope.launch { _events.send(WalkthroughEvent.NavigateEvent.ToStepFour) }
        }

        override fun onGoToStepFiveClick() {
            viewModelScope.launch { _events.send(WalkthroughEvent.NavigateEvent.ToStepFive) }
        }

        override fun onBackClick() {
            viewModelScope.launch { _events.send(WalkthroughEvent.NavigateEvent.GoBack) }
        }

        override fun onCompleteWalkthroughClick() {
            resetWalkthrough()
            viewModelScope.launch { _events.send(WalkthroughEvent.NavigateEvent.RestartWalkthrough) }
        }

        override fun onBiometricsAuthFailure(msg: String?) {
            viewModelScope.launch {
                if (msg != null) {
                    _events.send(WalkthroughEvent.Biometrics.BiometricError(msg))
                } else {
                    _events.send(WalkthroughEvent.Biometrics.BiometricAuthenticationFailure)
                }
            }
        }

        override fun onBiometricsUnavailable() {
            viewModelScope.launch {
                _events.send(WalkthroughEvent.Biometrics.BiometricsUnavailable)
            }
        }

        private fun resetKey() {
            key = null
            generatedKey.update { null }
            publicKey.update { null }
        }

        private fun resetDid() {
            didResult = null
            did.update { null }
        }

        private fun resetSignedResult() {
            verificationResult.update { null }
            signedOutput.update { null }
        }

        private fun resetWalkthrough() {
            resetKey()
            resetDid()
            resetSignedResult()
            plainText.update { "" }
        }
    }
}


