package id.walt.androidSample.ui

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import id.walt.crypto.keys.LocalKeyMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface MainViewModel {

    val plainText: StateFlow<String>

    val signature: StateFlow<ByteArray?>

    fun onSignRaw(plainText: String)

    fun onVerifyPlainText(signed: ByteArray, detachedPlaintext: ByteArray?): Result<ByteArray>

    fun onPlainTextChange(plainText: String)

    fun onClearInput()

    class Fake : MainViewModel {

        override val plainText = MutableStateFlow("placeholder text")
        override val signature = MutableStateFlow<ByteArray?>(null)

        override fun onSignRaw(plainText: String) = Unit
        override fun onVerifyPlainText(signed: ByteArray, detachedPlaintext: ByteArray?): Result<ByteArray> {
            return Result.success("".encodeToByteArray())
        }

        override fun onPlainTextChange(plainText: String) = Unit
        override fun onClearInput() = Unit

    }

    class Default : MainViewModel {

        private val viewModelScope = CoroutineScope(Dispatchers.Main.immediate)

        override val plainText = MutableStateFlow("")
        override val signature = MutableStateFlow<ByteArray?>(null)

        private var localKey: LocalKey? = null

        override fun onSignRaw(plainText: String) {
            viewModelScope.launch {
                val localKey = LocalKey.generate(KeyType.RSA, LocalKeyMetadata())
                val signedContent = localKey.signRaw(plainText.toByteArray())
                signature.value = signedContent
            }
        }

        override fun onVerifyPlainText(signed: ByteArray, detachedPlaintext: ByteArray?): Result<ByteArray> {
            TODO("Not yet implemented")
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