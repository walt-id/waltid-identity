package id.walt.did.dids.document


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/*
 * if JWK has `use`="sig", no `keyAgreement` is included
 * if JWK has `use`="enc", _only_ `keyAgreenment` is included
 *
 * curve "ed25519": only `use`="sig"
 * curve  "x25519": only `use`="enc"
 *
 * update & deactivate not supported
 */

@Serializable
data class DidJwkDocument(
    @SerialName("@context")
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

    constructor(did: String, didJwk: JsonObject) : this(
        context = DEFAULT_CONTEXT,
        id = did,
        verificationMethod = listOf(VerificationMethod(did, "JsonWebKey2020", did, didJwk)),

        assertionMethod = listOf(did),
        authentication = listOf(did),
        capabilityInvocation = listOf(did),
        capabilityDelegation = listOf(did),
        keyAgreement = listOf(did),
    )
}
