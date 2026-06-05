package id.walt.androidSample.app.features.walkthrough

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.walt.androidSample.app.features.walkthrough.model.*
import id.walt.crypto.AndroidKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.DidResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
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
            MutableStateFlow("{\"kty\":\"RSA\",\"n\":\"...\",\"e\":\"AQAB\"}")
        override val methodOptions = MethodOption.all()
        override val selectedMethod = MutableStateFlow(MethodOption.Key)
        override val did = MutableStateFlow("did:jwk:...")
        override val plainText = MutableStateFlow("")
        override val signOptions = SignOption.all()
        override val selectedSignOption = MutableStateFlow(SignOption.Raw)
        override val signedOutput = MutableStateFlow("QmhjUDl3...")
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

        private var key: Key? = null
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
                    val keyType = when (selectedKeyAlgorithm.value) {
                        KeyAlgorithmOption.RSA -> KeyType.RSA
                        KeyAlgorithmOption.Secp256r1 -> KeyType.secp256r1
                    }
                    val generatedKey = AndroidKey.Hardware.create(AndroidKey.Options(keyType = keyType))
                    key = generatedKey
                    this@Default.generatedKey.update { generatedKey.exportJWK() }
                } catch (e: InvalidAlgorithmParameterException) {
                    println("Error generating key: ${e.message}")
                    _events.send(WalkthroughEvent.Biometrics.SecureLockScreenNotEnabled)
                }
            }
        }

        override fun onRetrievePublicKeyClick() {
            key?.let { currentKey ->
                viewModelScope.launch {
                    publicKey.update { currentKey.getPublicKey().exportJWK() }
                }
            }
        }

        override fun onGenerateDIDClick() {
            viewModelScope.launch {
                key?.let { currentKey ->
                    DidService.minimalInit()
                    val result = when (selectedMethod.value) {
                        MethodOption.Key -> DidService.registerByKey("key", currentKey)
                        MethodOption.JWK -> DidService.registerByKey("jwk", currentKey)
                    }
                    didResult = result
                    did.update { result.did }
                }
            }
        }

        override fun onSignTextClick() {
            viewModelScope.launch {
                key?.let { currentKey ->
                    signedOutput.update {
                        when (selectedSignOption.value) {
                            SignOption.JWS -> {
                                val signedOutput = currentKey.signJws(
                                    plaintext = plainText.value.toByteArray(),
                                    headers = mapOf("kid" to JsonPrimitive(currentKey.getKeyId()))
                                )
                                signedOutputJWS = signedOutput
                                signedOutput
                            }

                            SignOption.Raw -> {
                                val signedByteArray = currentKey.signRaw(plainText.value.toByteArray()) as ByteArray
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
                key?.let { currentKey ->
                    when (selectedSignOption.value) {
                        SignOption.Raw -> {
                            signedOutputByteArray?.let { byteArrayToVerify ->
                                val result = currentKey.verifyRaw(byteArrayToVerify, plainText.value.toByteArray())
                                if (result.isSuccess) {
                                    verificationResult.update { VerificationResult.Success }
                                } else {
                                    verificationResult.update { VerificationResult.Failed }
                                }
                            }
                        }

                        SignOption.JWS -> {
                            signedOutputJWS?.let { signedJWSToVerify ->
                                val result = currentKey.verifyJws(signedJWSToVerify)
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


