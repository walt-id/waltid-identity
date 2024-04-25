package id.walt.did.dids.document

import id.walt.crypto.keys.Key
import id.walt.ebsi.did.DidEbsiBaseDocument
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@ExperimentalJsExport
@JsExport
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DidEbsiDocument(
    @EncodeDefault @SerialName("@context")  val context: List<String> = DidEbsiBaseDocument.DEFAULT_CONTEXT,
    val id: String, // did:ebsi:
    val controller: Set<String>? = null,
    val verificationMethod: List<VerificationMethod>?,
    val assertionMethod: List<String>? = null,
    val authentication: List<String>? = null,
    val capabilityInvocation: List<String>? = null,
    val capabilityDelegation: List<String>? = null,
    val keyAgreement: List<String>? = null
) {
    @Serializable
    data class VerificationMethod(
        val id: String, // did:ebsi:<identifier>#<keyID>
        val type: String, // JsonWebKey2020
        val controller: String, // did:ebsi:<identifier>
        val publicKeyJwk: JsonObject // json-web-key
    )

    fun toMap() = Json.encodeToJsonElement(this).jsonObject.toMap()

    @JsName("secondaryConstructor")
    constructor(did: String, identifier: String, didKey: JsonObject) : this(
        context = DidEbsiBaseDocument.DEFAULT_CONTEXT,
        id = did,
        verificationMethod = listOf(VerificationMethod("$did#$identifier", "JsonWebKey2020", did, didKey)),

        assertionMethod = listOf("$did#$identifier"),
        authentication = listOf("$did#$identifier"),
        capabilityInvocation = listOf("$did#$identifier"),
        capabilityDelegation = listOf("$did#$identifier"),
        keyAgreement = listOf("$did#$identifier"),
    )
}
