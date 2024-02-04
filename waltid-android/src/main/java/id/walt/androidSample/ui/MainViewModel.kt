package id.walt.androidSample.ui

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import id.walt.crypto.keys.LocalKeyMetadata
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

    val events: Flow<Event>

    fun onSignRaw(plainText: String)

    fun onVerifyPlainText(signature: ByteArray, plainText: ByteArray?)

    fun onPlainTextChange(plainText: String)

    fun onClearInput()

    sealed interface Event {
        data object SignatureVerified : Event
        data object SignatureInvalid : Event
    }

    class Fake : MainViewModel {

        override val plainText = MutableStateFlow("placeholder text")
        override val signature = MutableStateFlow<ByteArray?>(null)
        override val events = emptyFlow<Event>()

        override fun onSignRaw(plainText: String) = Unit
        override fun onVerifyPlainText(signature: ByteArray, plainText: ByteArray?) {
            Result.success("".encodeToByteArray())
        }

        override fun onPlainTextChange(plainText: String) = Unit
        override fun onClearInput() = Unit

    }

    class Default : MainViewModel {

        private val viewModelScope = CoroutineScope(Dispatchers.Main.immediate)

        override val plainText = MutableStateFlow("")
        override val signature = MutableStateFlow<ByteArray?>(null)

        private val eventsChannel = Channel<Event>()
        override val events = eventsChannel.receiveAsFlow()

        private var localKey: LocalKey? = null

        override fun onSignRaw(plainText: String) {
            viewModelScope.launch {
                LocalKey.generate(KeyType.RSA, LocalKeyMetadata()).run {
                    localKey = this
                    val signedContent = this.signRaw(plainText.toByteArray())
                    signature.value = signedContent
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

        override fun onPlainTextChange(plainText: String) {
            this.plainText.value = plainText
        }

        override fun onClearInput() {
            plainText.value = ""
            signature.value = null
        }

    }
}