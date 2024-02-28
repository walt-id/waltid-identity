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

/*
 * if JWK has `use`="sig", no `keyAgreement` is included
 * if JWK has `use`="enc", _only_ `keyAgreenment` is included
 *
 * curve "ed25519": only `use`="sig"
 * curve  "x25519": only `use`="enc"
 *
 * update & deactivate not supported
 */

@ExperimentalJsExport
@JsExport
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DidJwkDocument(
    @EncodeDefault @SerialName("@context")
    val context: List<String> = DEFAULT_CONTEXT,
    val id: String, // did:jwk:${base64url-value}

    val verificationMethod: List<VerificationMethod>?,
    val assertionMethod: List<String>?,
    val authentication: List<String>?,
    val capabilityInvocation: List<String>?,
    val capabilityDelegation: List<String>?,
    val keyAgreement: List<String>?
) {
    companion object {
        private val DEFAULT_CONTEXT = listOf("https://www.w3.org/ns/did/v1", "https://w3id.org/security/suites/jws-2020/v1")
    }

    @Serializable
    data class VerificationMethod(
        val id: String, // did:jwk:${base64url-value}#0
        val type: String, // JsonWebKey2020
        val controller: String, // did:jwk:${base64url-value}
        val publicKeyJwk: JsonObject // json-web-key
    )

    fun toMap() = Json.encodeToJsonElement(this).jsonObject.toMap()

    @JsName("secondaryConstructor")
    constructor(did: String, didJwk: JsonObject) : this(
        context = DEFAULT_CONTEXT,
        id = did,
        verificationMethod = listOf(VerificationMethod("$did#0", "JsonWebKey2020", did, didJwk)),

        assertionMethod = listOf("$did#0"),
        authentication = listOf("$did#0"),
        capabilityInvocation = listOf("$did#0"),
        capabilityDelegation = listOf("$did#0"),
        keyAgreement = listOf("$did#0"),
    )
}
