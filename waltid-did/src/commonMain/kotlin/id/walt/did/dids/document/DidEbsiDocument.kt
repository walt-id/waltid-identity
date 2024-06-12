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
    }

    @Serializable
    data class VerificationMethod(
        val id: String, // did:ebsi:
        val type: String, // JsonWebKey2020
        val controller: String, // did:ebsi:
        val publicKeyJwk: JsonObject // jwk
    )

    @Transient
    private val json = Json {
        explicitNulls = false
    }

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
//    constructor(did: String,
//        secp256k1KeyId: String,
//        secp256k1Key: JsonObject,
//        secp256r1KeyId: String,
//        secp256r1Key: JsonObject
//    ) : this(
//        context = DEFAULT_CONTEXT,
//        id = did,
//        verificationMethod = listOf(
//            VerificationMethod("$did#$secp256k1KeyId", "JsonWebKey2020", secp256k1KeyId, secp256k1Key),
//            VerificationMethod("$did#$secp256r1KeyId", "JsonWebKey2020", secp256r1KeyId, secp256r1Key),
//        ),
//        authentication = listOf(
//            "$did#$secp256k1KeyId",
//            "$did#$secp256r1KeyId",
//        ),
//        assertionMethod = listOf(
//            "$did#$secp256k1KeyId",
//            "$did#$secp256r1KeyId",
//        ),
//        capabilityInvocation = listOf(
//            "$did#$secp256k1KeyId",
//            "$did#$secp256r1KeyId",
//        ),
//        capabilityDelegation = listOf(
//            "$did#$secp256k1KeyId",
//            "$did#$secp256r1KeyId",
//        ),
//        keyAgreement = listOf(
//            "$did#$secp256k1KeyId",
//            "$did#$secp256r1KeyId",
//        ),
//    )
}