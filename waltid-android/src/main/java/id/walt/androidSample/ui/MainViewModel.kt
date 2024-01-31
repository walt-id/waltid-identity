package id.walt.androidSample.ui

import id.walt.credentials.CredentialBuilder
import id.walt.credentials.CredentialBuilderType
import id.walt.credentials.vc.vcs.W3CVC
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import id.walt.crypto.keys.LocalKeyMetadata
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.days

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

        init {
            viewModelScope.launch {
                DidService.minimalInit()

                val keyBasedDid = DidService.register(DidKeyCreateOptions())
                val jwkBasedDid = DidService.register(DidJwkCreateOptions())

                didText.value = """
                    Key-Based DID Key: ${keyBasedDid.did}
                    
                    JWK-Based DID Key: ${jwkBasedDid.did}
                """.trimIndent()

                val vc = generateVerifiedCredential()

                val myIssuerKey = LocalKey.generate(KeyType.Ed25519)
                val myIssuerDid = DidService.registerByKey("key", myIssuerKey).did

                val mySubjectDid = "did:key:xyz"

                verifiedCredentialJSON.value = vc.toPrettyJson()

                // vc refers to the credential created in the previous section
                val signed: String = vc.signJws(myIssuerKey, myIssuerDid, mySubjectDid)
                signedVC.value = signed
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

        private fun generateVerifiedCredential(): W3CVC {
            return CredentialBuilder(CredentialBuilderType.W3CV2CredentialBuilder).apply {
                // Adds context next to default "https://www.w3.org/2018/credentials/v1"
                addContext("https://purl.imsglobal.org/spec/ob/v3p0/context-3.0.2.json")

                // Adds type next to default "VerifiableCredential"
                addType("OpenBadgeCredential")

                credentialId = "urn:uuid:4177e048-9a4a-474e-9dc6-aed4e61a6439"

                issuerDid = "did:key:z6MksFnax6xCBWdo6hhfZ7pEsbyaq9MMNdmABS1NrcCDZJr3"

                // Sets issuance date to current time - 1.5 min
                validFromNow()

                // Adds expiration date
                validFor(2.days)

                subjectDid = "did:key:z6MksFnax6xCBWdo6hhfZ7pEsbyaq9MMNdmABS1NrcCDZJr3"

                // Used to add any custom data
                useData("name", JsonPrimitive("JFF x vc-edu PlugFest 3 Interoperability"))

                // Used to insert credential subject data
                useCredentialSubject(
                    mapOf(
                        "type" to listOf("AchievementSubject"),
                        "achievement" to mapOf(
                            "id" to "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
                            "type" to listOf("Achievement"),
                            "name" to "JFF x vc-edu PlugFest 3 Interoperability",
                            "description" to "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
                            "criteria" to mapOf(
                                "type" to "Criteria",
                                "narrative" to "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
                            ),
                            "image" to mapOf(
                                "id" to "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
                                "type" to "Image"
                            )
                        )
                    ).toJsonObject()
                )
            }.buildW3C() // Builds the credential and returns W3CVC object
        }

    }
}