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

    val encryptedText: StateFlow<String>

    val didText: StateFlow<String>

    val verifiedCredentialJSON: StateFlow<String>

    val signedVC: StateFlow<String>

    fun onEncrypt(plainText: String)

    fun onPlainTextChange(plainText: String)

    fun onClearInput()

    class Fake : MainViewModel {

        override val plainText = MutableStateFlow("placeholder text")
        override val encryptedText = MutableStateFlow("")
        override val didText = MutableStateFlow("")
        override val verifiedCredentialJSON = MutableStateFlow("")
        override val signedVC = MutableStateFlow("")

        override fun onEncrypt(plainText: String) = Unit
        override fun onPlainTextChange(plainText: String) = Unit
        override fun onClearInput() = Unit

    }

    class Default : MainViewModel {

        private val viewModelScope = CoroutineScope(Dispatchers.Main.immediate)

        override val plainText = MutableStateFlow("")
        override val encryptedText = MutableStateFlow("")
        override val didText = MutableStateFlow("")
        override val verifiedCredentialJSON = MutableStateFlow("")
        override val signedVC = MutableStateFlow("")

        private var localKey: LocalKey? = null

        init {
            viewModelScope.launch {
                localKey = LocalKey.generate(KeyType.RSA, LocalKeyMetadata())
            }
        }

        override fun onEncrypt(plainText: String) {
            viewModelScope.launch {
                val localKey = LocalKey.generate(KeyType.RSA, LocalKeyMetadata())
                val signedContent = localKey.signJws(plainText.toByteArray())
                encryptedText.value = signedContent
            }
        }

        override fun onPlainTextChange(plainText: String) {
            this.plainText.value = plainText
        }

        override fun onClearInput() {
            plainText.value = ""
            encryptedText.value = ""
        }

    }
}