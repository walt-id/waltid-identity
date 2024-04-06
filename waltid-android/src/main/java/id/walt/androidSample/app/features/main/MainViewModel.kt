package id.walt.androidSample.app.features.main

import android.util.Base64
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import id.walt.credentials.CredentialBuilder
import id.walt.credentials.CredentialBuilderType
import id.walt.crypto.keys.AndroidKey
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKeyMetadata
import id.walt.did.dids.DidService
import id.walt.sdjwt.SDJwt
import id.walt.sdjwt.SDPayload
import id.walt.sdjwt.SimpleJWTCryptoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.UnsupportedEncodingException
import kotlin.js.ExperimentalJsExport


interface MainViewModel {

    val plainText: StateFlow<String>
    val displayText: StateFlow<String?>
    val signature: StateFlow<ByteArray?>

    val events: Flow<Event>

    fun onSignRaw(plainText: String, keyType: KeyType)

    fun onSignJWS(plainText: String, keyType: KeyType)

    fun onVerifyPlainText(signature: ByteArray, plainText: ByteArray?)

    fun onPlainTextChange(plainText: String)

    fun onRetrievePublicKey()

    fun onBiometricsUnavailable()

    fun onBiometricsAuthFailure()

    fun onGenerateDid()

    fun onSignCredential()

    fun onCreateAndSignSDJWT()

    fun onClearInput()

    sealed interface Event {
        data object SignatureVerified : Event
        data object SignatureInvalid : Event
        data object BiometricsUnavailable : Event
        data object CredentialSignFailure : Event
        data object BiometricAuthenticationFailure : Event
        data class SignedWithKey(val key: KeyType) : Event
        data object GeneralSuccess : Event
        data object NavigateToResult : Event
    }

    class Fake : MainViewModel {

        override val plainText = MutableStateFlow("")
        override val displayText = MutableStateFlow<String?>(null)
        override val signature = MutableStateFlow<ByteArray?>(null)
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
        override fun onSignCredential() = Unit
        override fun onSignJWS(plainText: String, keyType: KeyType) = Unit
        override fun onCreateAndSignSDJWT() = Unit

    }

    class Default : MainViewModel {

        private val viewModelScope = CoroutineScope(Dispatchers.Main.immediate)

        override val plainText = MutableStateFlow("")
        override val displayText = MutableStateFlow<String?>(null)
        override val signature = MutableStateFlow<ByteArray?>(null)

        private val eventsChannel = Channel<Event>()
        override val events = eventsChannel.receiveAsFlow()

        private var androidKey: AndroidKey? = null

        private var did: String? = null

        override fun onSignRaw(plainText: String, keyType: KeyType) {
            viewModelScope.launch {
                AndroidKey.generate(keyType, LocalKeyMetadata()).run {
                    androidKey = this
                    val signedContent = this.signRaw(plainText.toByteArray())
                    displayText.value = Base64.encodeToString(signedContent, Base64.DEFAULT)
                    signature.value = signedContent
                    eventsChannel.send(Event.NavigateToResult)
                }
            }
        }

        override fun onSignJWS(plainText: String, keyType: KeyType) {
            viewModelScope.launch {
                AndroidKey.generate(keyType, LocalKeyMetadata()).run {
                    androidKey = this
                    signJws(plainText.toByteArray(), mapOf("kid" to this.getKeyId())).also {
                        displayText.value = it
                        eventsChannel.send(Event.NavigateToResult)
                    }
                }
            }
        }

        override fun onVerifyPlainText(signature: ByteArray, plainText: ByteArray?) {
            viewModelScope.launch {
                androidKey?.let {
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
                displayText.value = androidKey?.getPublicKey().toString()
                eventsChannel.send(Event.NavigateToResult)
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
                androidKey?.let {
                    val didKey = DidService.registerByKey("key", it)
                    displayText.value = didKey.did
                    did = didKey.did
                    eventsChannel.send(Event.NavigateToResult)
                }
            }
        }

        @OptIn(ExperimentalJsExport::class)
        override fun onCreateAndSignSDJWT() {
            viewModelScope.launch {
                // Shared secret for HMAC crypto algorithm
                val sharedSecret = "ef23f749-7238-481a-815c-f0c2157dfa8e"

                // Create SimpleJWTCryptoProvider with MACSigner and MACVerifier
                val cryptoProvider = SimpleJWTCryptoProvider(JWSAlgorithm.HS256, MACSigner(sharedSecret), MACVerifier(sharedSecret))

                // Create original JWT claims set, using nimbusds claims set builder
                val originalClaimsSet = JWTClaimsSet.Builder()
                    .subject("123")
                    .audience("456")
                    .claim("name", "Jane")
                    .claim("email", "janedoe@gmail.com")
                    .claim("family_name", "Doe")
                    .claim("admin", true)
                    .build()

                // Create undisclosed claims set, by removing e.g. subject property from original claims set
                val undisclosedClaimsSet = JWTClaimsSet.Builder(originalClaimsSet)
                    .subject(null)
                    .claim("admin", null)
                    .claim("family_name", null)
                    .build()

                // Create SD payload by comparing original claims set with undisclosed claims set
                val sdPayload = SDPayload.createSDPayload(originalClaimsSet, undisclosedClaimsSet)

                // Create and sign SD-JWT using the generated SD payload and the previously configured crypto provider
                val sdJwt = SDJwt.sign(sdPayload, cryptoProvider)

                fun getJson(strEncoded: String): String {
                    val decodedBytes = Base64.decode(strEncoded, Base64.URL_SAFE)
                    return String(decodedBytes, charset("UTF-8"))
                }

                val decoded = try {
                    val split = sdJwt.toString().split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val header = getJson(split[0])
                    val body = getJson(split[1])
                    "HEADER --> $header\n\nBODY --> $body\n"
                } catch (e: UnsupportedEncodingException) {
                    null
                }

                displayText.value = "$sdJwt \n\n $decoded"
                eventsChannel.send(Event.NavigateToResult)
            }
        }

        override fun onSignCredential() {
            viewModelScope.launch {
                val credential = CredentialBuilder(CredentialBuilderType.W3CV11CredentialBuilder).buildW3C()

                val key = androidKey
                val d = did
                if (key != null && d != null) {
                    val mySubjectDid = "did:key:xyz"

                    val signed: String = credential.signJws(key, d, mySubjectDid)

                    displayText.value = signed
                    eventsChannel.send(Event.NavigateToResult)
                } else {
                    eventsChannel.send(Event.CredentialSignFailure)
                }
            }
        }

        override fun onClearInput() {
            displayText.value = null
            plainText.value = ""
            signature.value = null
        }

    }
}