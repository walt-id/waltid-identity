package id.walt.did.dids.document

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
@OptIn(ExperimentalSerializationApi::class, ExperimentalJsExport::class)
@Serializable
data class DidEbsiDocument(
    @EncodeDefault @SerialName("@context") val context: List<String> = DEFAULT_CONTEXT,
    val id: String, // did:ebsi:
    val controller: List<String>?,
    val verificationMethod: List<VerificationMethod>?,
    val authentication: List<String>?,
    val assertionMethod: List<String>?,
    val capabilityInvocation: List<String>?,
    val capabilityDelegation: List<String>?,
    val keyAgreement: List<String>?,
) {
    companion object {
        private val DEFAULT_CONTEXT =
            listOf("https://www.w3.org/ns/did/v1", "https://w3id.org/security/suites/jws-2020/v1")
        private val json = Json {
            explicitNulls = false
        }
    }

    @Serializable
    data class VerificationMethod(
        val id: String, // did:ebsi:
        val type: String, // JsonWebKey2020
        val controller: String, // did:ebsi:
        val publicKeyJwk: JsonObject // jwk
    )

    fun toMap() = json.encodeToJsonElement(this).jsonObject.toMap()

    @JsName("secondaryConstructor")
    constructor(didDoc: DidDocument) : this(
        context = DEFAULT_CONTEXT,
        id = didDoc["id"]!!.jsonPrimitive.content,
        controller = didDoc["controller"]?.jsonArray?.map {
            it.jsonPrimitive.content
        },
        verificationMethod = didDoc["verificationMethod"]?.jsonArray?.map {
            val verificationMethod = it.jsonObject
            val id = verificationMethod["id"]!!.jsonPrimitive.content
            val type = verificationMethod["type"]!!.jsonPrimitive.content
            val controller = verificationMethod["controller"]!!.jsonPrimitive.content
            val publicKeyJwk = verificationMethod["publicKeyJwk"]!!.jsonObject
            VerificationMethod(id, type, controller, publicKeyJwk)
        },
        authentication = didDoc["authentication"]?.jsonArray?.map {
            it.jsonPrimitive.content
        },
        assertionMethod = didDoc["assertionMethod"]?.jsonArray?.map {
            it.jsonPrimitive.content
        },
        capabilityInvocation = didDoc["capabilityInvocation"]?.jsonArray?.map {
            it.jsonPrimitive.content
        },
        capabilityDelegation = didDoc["capabilityDelegation"]?.jsonArray?.map {
            it.jsonPrimitive.content
        },
        keyAgreement = didDoc["keyAgreement"]?.jsonArray?.map {
            it.jsonPrimitive.content
        },
    )
}