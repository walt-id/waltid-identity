package id.walt.androidSample.app

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import id.walt.crypto.keys.LocalKeyMetadata
import id.walt.did.dids.DidService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

interface MainViewModel {

    val plainText: StateFlow<String>

    val signature: StateFlow<ByteArray?>

    val publicKey: StateFlow<LocalKey?>

    val did: StateFlow<String?>

    val jws: StateFlow<String?>

    val events: Flow<Event>

    fun onSignRaw(plainText: String, keyType: KeyType)

    fun onSignJWS(plainText: String, keyType: KeyType)

    fun onVerifyPlainText(signature: ByteArray, plainText: ByteArray?)

    fun onPlainTextChange(plainText: String)

    fun onRetrievePublicKey()

    fun onBiometricsUnavailable()

    fun onBiometricsAuthFailure()

    fun onGenerateDid()

    fun onClearInput()

    sealed interface Event {
        data object SignatureVerified : Event
        data object SignatureInvalid : Event
        data object BiometricsUnavailable : Event
        data object BiometricAuthenticationFailure : Event
        data class SignedWithKey(val key: KeyType) : Event
    }

    class Fake : MainViewModel {

        override val plainText = MutableStateFlow("placeholder text")
        override val signature = MutableStateFlow<ByteArray?>(null)
        override val publicKey = MutableStateFlow<LocalKey?>(null)
        override val did = MutableStateFlow<String>("")
        override val jws = MutableStateFlow<String?>(null)
        override val events = emptyFlow<Event>()

        override fun onSignRaw(plainText: String, keyType: KeyType) = Unit
        override fun onVerifyPlainText(signature: ByteArray, plainText: ByteArray?) {
            Result.success("".encodeToByteArray())
        }

        override fun onPlainTextChange(plainText: String) = Unit
        override fun onClearInput() = Unit
        override fun onRetrievePublicKey() = Unit
        override fun onBiometricsUnavailable() = Unit
        override fun onBiometricsAuthFailure() = Unit
        override fun onGenerateDid() = Unit
        override fun onSignJWS(plainText: String, keyType: KeyType) = Unit

    }

    class Default : MainViewModel {

        private val viewModelScope = CoroutineScope(Dispatchers.Main.immediate)

        override val plainText = MutableStateFlow("")
        override val signature = MutableStateFlow<ByteArray?>(null)
        override val publicKey = MutableStateFlow<LocalKey?>(null)
        override val did = MutableStateFlow<String?>(null)
        override val jws = MutableStateFlow<String?>(null)

        private val eventsChannel = Channel<Event>()
        override val events = eventsChannel.receiveAsFlow()

        private var localKey: LocalKey? = null

        override fun onSignRaw(plainText: String, keyType: KeyType) {
            viewModelScope.launch {
                LocalKey.generate(keyType, LocalKeyMetadata()).run {
                    localKey = this
                    val signedContent = this.signRaw(plainText.toByteArray())
                    signature.value = signedContent
                    eventsChannel.send(Event.SignedWithKey(keyType))
                }
            }
        }

        override fun onSignJWS(plainText: String, keyType: KeyType) {
            viewModelScope.launch {
                LocalKey.generate(keyType, LocalKeyMetadata()).run {
                    localKey = this
                    signJws(plainText.toByteArray(), mapOf("kid" to this.getKeyId())).also {
                        jws.value = it
                        eventsChannel.send(Event.SignedWithKey(keyType))
                    }
                }
            }
        }

        override fun onVerifyPlainText(signature: ByteArray, plainText: ByteArray?) {
            viewModelScope.launch {
                localKey?.let {
                    val result = it.verifyRaw(signature, plainText)
                    if (result.isSuccess) {
                        eventsChannel.send(Event.SignatureVerified)
                    } else {
                        eventsChannel.send(Event.SignatureInvalid)
                    }
                }
            }
        }

        override fun onRetrievePublicKey() {
            viewModelScope.launch {
                publicKey.value = localKey?.getPublicKey()
            }
        }

        override fun onPlainTextChange(plainText: String) {
            this.plainText.value = plainText
        }

        override fun onBiometricsUnavailable() {
            viewModelScope.launch {
                eventsChannel.send(Event.BiometricsUnavailable)
            }
        }

        override fun onBiometricsAuthFailure() {
            viewModelScope.launch {
                eventsChannel.send(Event.BiometricAuthenticationFailure)
            }
        }

        override fun onGenerateDid() {
            viewModelScope.launch {
                DidService.minimalInit()
                localKey?.let {
                    val didKey = DidService.registerByKey("key", it)
                    did.value = didKey.did
                }
            }
        }

        override fun onClearInput() {
            plainText.value = ""
            signature.value = null
            publicKey.value = null
            did.value = null
            jws.value = null
        }

    }
}