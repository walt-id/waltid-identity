package id.walt.openid4vci.handlers.credential

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.did.dids.DidUtils
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.sdjwt.SDMap
import id.walt.w3c.CredentialBuilder
import id.walt.w3c.CredentialBuilderType
import id.walt.w3c.issuance.Issuer.mergingJwtIssue
import id.walt.w3c.issuance.Issuer.mergingSdJwtIssue
import id.walt.w3c.vc.vcs.W3CV11DataModel
import id.walt.w3c.vc.vcs.W3CV2DataModel
import id.walt.w3c.vc.vcs.W3CVC
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

object W3cJwtVcCredentialSigner {
    suspend fun generateW3CJwtVC(
        credentialRequest: CredentialRequest,
        credentialData: JsonObject,
        issuerKey: Key,
        issuerId: String,
        selectiveDisclosure: SDMap? = null,
        dataMapping: JsonObject? = null,
        x5Chain: List<String>? = null,
        display: List<CredentialDisplay>? = null,
        credentialStatus: JsonElement? = null,
        w3cVersion: String? = null,
    ): String {
        val proofHeader = credentialRequest.proofs?.jwt?.let { JwtUtils.parseJWTHeader(it.first()) }
            ?: throw IllegalArgumentException("Missing JWT proof in proofs")

        val holderKid = proofHeader[JWT_HEADER_KID]?.jsonPrimitive?.content

        val holderDid =
            if (!holderKid.isNullOrEmpty() && DidUtils.isDidUrl(holderKid)) holderKid.substringBefore("#") else null

        val additionalJwtHeaders = x5Chain?.let {
            mapOf(JWT_HEADER_X5C to JsonArray(it.map { cert -> JsonPrimitive(cert) }))
        } ?: mapOf()

        val vcPayload = credentialStatus?.let { status ->
            JsonObject(credentialData.toMutableMap().apply { put("credentialStatus", status) })
        } ?: credentialData

        return W3CVC(vcPayload).let { vc ->
            val builderType = w3cVersion?.let { version ->
                val serialNameMap = mapOf(
                    "W3CV11" to CredentialBuilderType.W3CV11CredentialBuilder,
                    "W3CV2" to CredentialBuilderType.W3CV2CredentialBuilder,
                )
                CredentialBuilderType.entries.firstOrNull { it.name == version }
                    ?: serialNameMap[version]
                    ?: CredentialBuilderType.entries.firstOrNull {
                        it.name.equals(version, ignoreCase = true)
                    }
                    ?: serialNameMap.entries.firstOrNull {
                        it.key.equals(version, ignoreCase = true)
                    }?.value
                    ?: throw IllegalArgumentException(
                        "Unsupported w3cVersion: '$version'. Supported values: ${
                            (CredentialBuilderType.entries.map { it.name } + serialNameMap.keys).joinToString { "'$it'" }
                        }"
                    )
            }
            val w3cVc = when (builderType) {
                CredentialBuilderType.W3CV2CredentialBuilder -> {
                    val v2ContextUri = W3CV2DataModel.defaultContext.first()
                    val v11ContextUri = W3CV11DataModel.defaultContext.first()
                    val base = if (vc.isV2()) vc.toMutableMap() else {
                        val existing = vcPayload["@context"]
                            ?.let {
                                if (it is JsonArray) it.map { e -> e.jsonPrimitive.content }
                                else listOf(it.jsonPrimitive.content)
                            }
                            ?: emptyList()
                        val merged = (listOf(v2ContextUri) + existing).distinct()
                        vcPayload.toMutableMap().also { map ->
                            map["@context"] = JsonArray(merged.map { JsonPrimitive(it) })
                        }
                    }
                    (base["@context"] as? JsonArray)?.let { arr ->
                        val cleaned = arr.filter { it.jsonPrimitive.contentOrNull != v11ContextUri }
                        if (cleaned.size != arr.size) base["@context"] = JsonArray(cleaned)
                    }
                    base.remove("issuanceDate")?.let { v -> if ("validFrom" !in base) base["validFrom"] = v }
                    base.remove("expirationDate")?.let { v -> if ("validUntil" !in base) base["validUntil"] = v }
                    W3CVC(base)
                }

                else -> builderType?.let {
                    val builder = CredentialBuilder(it)
                    builder.useCredentialSubject(vcPayload)
                    builder.buildW3C()
                } ?: vc
            }
            val context = mapOf(
                "subjectDid" to holderDid,
                "issuerDid" to issuerId,
                "display" to Json.encodeToJsonElement(display ?: emptyList()).jsonArray,
            ).filterValues {
                when (it) {
                    is JsonElement -> it !is JsonNull && (it !is JsonObject || it.isNotEmpty()) && (it !is JsonArray || it.isNotEmpty())
                    else -> it != null && it.toString().isNotEmpty()
                }
            }.mapValues { (_, value) ->
                when (value) {
                    is JsonElement -> value
                    else -> JsonPrimitive(value.toString())
                }
            }
            when (selectiveDisclosure.isNullOrEmpty()) {
                true -> w3cVc.mergingJwtIssue(
                    issuerKey = issuerKey,
                    issuerId = issuerId,
                    subjectDid = holderDid ?: "",
                    mappings = dataMapping ?: JsonObject(emptyMap()),
                    additionalJwtHeader = additionalJwtHeaders,
                    display = Json.encodeToJsonElement(display ?: emptyList()).jsonArray,
                    additionalJwtOptions = emptyMap(),
                    context = context
                )

                else -> w3cVc.mergingSdJwtIssue(
                    issuerKey = issuerKey,
                    issuerId = issuerId,
                    subjectDid = holderDid ?: "",
                    mappings = dataMapping ?: JsonObject(emptyMap()),
                    additionalJwtHeaders = additionalJwtHeaders,
                    additionalJwtOptions = emptyMap(),
                    display = Json.encodeToJsonElement(display ?: emptyList()).jsonArray,
                    disclosureMap = selectiveDisclosure,
                    context = context
                )
            }
        }
    }

    private const val JWT_HEADER_KID = "kid"
    private const val JWT_HEADER_X5C = "x5c"
}


object JwtUtils {
    fun parseJWTPayload(token: String): JsonObject {
        return token.substringAfter(".").substringBefore(".").let {
            Json.decodeFromString(it.decodeFromBase64Url().decodeToString())
        }
    }

    fun parseJWTHeader(token: String): JsonObject {
        return token.substringBefore(".").let {
            Json.decodeFromString(it.decodeFromBase64Url().decodeToString())
        }
    }
}
