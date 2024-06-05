package id.walt.did.dids.document

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

@JsExport
@OptIn(ExperimentalSerializationApi::class, ExperimentalJsExport::class)
@Serializable
data class DidKeyDocument(
    @EncodeDefault @SerialName("@context") val context: List<String> = DEFAULT_CONTEXT,
    val id: String, // did:key:

    val verificationMethod: List<VerificationMethod>?,
    val assertionMethod: List<String>?,
    val authentication: List<String>?,
    val capabilityInvocation: List<String>?,
    val capabilityDelegation: List<String>?,
    val keyAgreement: List<String>?
) {
    companion object {
        private val DEFAULT_CONTEXT =
            listOf("https://www.w3.org/ns/did/v1", "https://w3id.org/security/suites/jws-2020/v1")
    }

    @Serializable
    data class VerificationMethod(
        val id: String, // did:key:
        val type: String, // JsonWebKey2020
        val controller: String, // did:key:
        val publicKeyJwk: JsonObject // json-web-key
    )

    fun toMap() = Json.encodeToJsonElement(this).jsonObject.toMap()

    @JsName("secondaryConstructor")
    constructor(did: String, identifier: String, didKey: JsonObject) : this(
        context = DEFAULT_CONTEXT,
        id = did,
        verificationMethod = listOf(VerificationMethod("$did#$identifier", "JsonWebKey2020", did, didKey)),

        assertionMethod = listOf("$did#$identifier"),
        authentication = listOf("$did#$identifier"),
        capabilityInvocation = listOf("$did#$identifier"),
        capabilityDelegation = listOf("$did#$identifier"),
        keyAgreement = listOf("$did#$identifier"),
    )
}
