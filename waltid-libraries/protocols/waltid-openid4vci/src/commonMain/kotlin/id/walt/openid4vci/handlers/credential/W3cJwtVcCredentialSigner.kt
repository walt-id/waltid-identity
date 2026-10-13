package id.walt.openid4vci.handlers.credential

import id.walt.certificate.x509.X509Certificate
import id.walt.crypto.keys.Key
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.proofs.VerifiedCredentialBinding
import id.walt.openid4vci.proofs.invalidCredentialProof
import id.walt.sdjwt.SDJwtVC.Companion.SD_JWT_VC_TYPE_HEADER
import id.walt.sdjwt.SDMap
import id.walt.w3c.CredentialBuilder
import id.walt.w3c.CredentialBuilderType
import id.walt.credentials.issuance.MergingIssuer.mergingJwtIssue
import id.walt.credentials.issuance.MergingIssuer.mergingSdJwtIssue
import id.walt.w3c.vc.vcs.W3CVC
import id.walt.w3c.vc.vcs.applyIssuedV2Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray

/** Generates credentials from an explicitly supplied binding. This signer rejects null bindings. */
object W3cJwtVcCredentialSigner {
    @Deprecated("Use the Crypto2Key overload")
    suspend fun generateW3CJwtVC(
        verifiedBinding: VerifiedCredentialBinding?,
        credentialData: JsonObject,
        issuerKey: Key,
        issuerId: String,
        selectiveDisclosure: SDMap? = null,
        dataMapping: JsonObject? = null,
        x5Chain: List<X509Certificate>? = null,
        display: List<CredentialDisplay>? = null,
        credentialStatus: JsonElement? = null,
        w3cVersion: String? = null,
    ): String = generateW3CJwtVC(
        credentialData = credentialData,
        issuerSigningKey = IssuerSigningKey.Legacy(issuerKey),
        issuerId = issuerId,
        selectiveDisclosure = selectiveDisclosure,
        dataMapping = dataMapping,
        x5Chain = x5Chain,
        display = display,
        credentialStatus = credentialStatus,
        w3cVersion = w3cVersion,
        verifiedBinding = verifiedBinding,
    )

    suspend fun generateW3CJwtVC(
        verifiedBinding: VerifiedCredentialBinding?,
        credentialData: JsonObject,
        issuerKey: Crypto2Key,
        algorithm: JwsAlgorithm,
        issuerId: String,
        selectiveDisclosure: SDMap? = null,
        dataMapping: JsonObject? = null,
        x5Chain: List<X509Certificate>? = null,
        display: List<CredentialDisplay>? = null,
        credentialStatus: JsonElement? = null,
        w3cVersion: String? = null,
    ): String = generateW3CJwtVC(
        credentialData = credentialData,
        issuerSigningKey = IssuerSigningKey.Crypto2(issuerKey, algorithm),
        issuerId = issuerId,
        selectiveDisclosure = selectiveDisclosure,
        dataMapping = dataMapping,
        x5Chain = x5Chain,
        display = display,
        credentialStatus = credentialStatus,
        w3cVersion = w3cVersion,
        verifiedBinding = verifiedBinding,
    )

    private suspend fun generateW3CJwtVC(
        verifiedBinding: VerifiedCredentialBinding?,
        credentialData: JsonObject,
        issuerSigningKey: IssuerSigningKey,
        issuerId: String,
        selectiveDisclosure: SDMap?,
        dataMapping: JsonObject?,
        x5Chain: List<X509Certificate>?,
        display: List<CredentialDisplay>?,
        credentialStatus: JsonElement?,
        w3cVersion: String?,
    ): String {
        val binding = verifiedBinding
            ?: throw invalidCredentialProof("W3C JWT VC issuance requires a verified credential binding")
        val holderDid = binding.holderDid

        val additionalJwtHeaders = x5Chain?.let {
            mapOf(JWT_HEADER_X5C to JsonArray(it.map { cert ->
                JsonPrimitive(
                    cert.encodedDer.toByteArray().encodeToBase64()
                )
            }))
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
                CredentialBuilderType.W3CV2CredentialBuilder -> W3CVC(vc.toMutableMap().applyIssuedV2Context())

                else -> builderType?.let {
                    val builder = CredentialBuilder(it)
                    builder.useCredentialSubject(vcPayload)
                    builder.buildW3C()
                } ?: vc
            }
            when (selectiveDisclosure.isNullOrEmpty()) {
                true -> when (issuerSigningKey) {
                    is IssuerSigningKey.Legacy -> w3cVc.mergingJwtIssue(
                        issuerKey = issuerSigningKey.key,
                        issuerId = issuerId,
                        subjectDid = holderDid ?: "",
                        mappings = dataMapping ?: JsonObject(emptyMap()),
                        additionalJwtHeader = additionalJwtHeaders,
                        display = Json.encodeToJsonElement(display ?: emptyList()).jsonArray,
                        additionalJwtOptions = emptyMap(),
                    )

                    is IssuerSigningKey.Crypto2 -> w3cVc.mergingJwtIssue(
                        issuerKey = issuerSigningKey.key,
                        algorithm = issuerSigningKey.algorithm,
                        issuerId = issuerId,
                        subjectDid = holderDid ?: "",
                        mappings = dataMapping ?: JsonObject(emptyMap()),
                        additionalJwtHeader = additionalJwtHeaders,
                        display = Json.encodeToJsonElement(display ?: emptyList()).jsonArray,
                        additionalJwtOptions = emptyMap(),
                    )
                }

                else -> {
                    val type = when (builderType) {
                        CredentialBuilderType.W3CV2CredentialBuilder -> SD_JWT_VC_TYPE_HEADER
                        // Including CredentialBuilderType.W3CV11CredentialBuilder
                        else -> "JWT"
                    }
                    when (issuerSigningKey) {
                        is IssuerSigningKey.Legacy -> w3cVc.mergingSdJwtIssue(
                            issuerKey = issuerSigningKey.key,
                            issuerId = issuerId,
                            subjectDid = holderDid ?: "",
                            mappings = dataMapping ?: JsonObject(emptyMap()),
                            additionalJwtHeaders = additionalJwtHeaders,
                            additionalJwtOptions = emptyMap(),
                            display = Json.encodeToJsonElement(display ?: emptyList()).jsonArray,
                            disclosureMap = selectiveDisclosure,
                            type = type,
                        )

                        is IssuerSigningKey.Crypto2 -> w3cVc.mergingSdJwtIssue(
                            issuerKey = issuerSigningKey.key,
                            algorithm = issuerSigningKey.algorithm,
                            issuerId = issuerId,
                            subjectDid = holderDid ?: "",
                            mappings = dataMapping ?: JsonObject(emptyMap()),
                            additionalJwtHeaders = additionalJwtHeaders,
                            additionalJwtOptions = emptyMap(),
                            display = Json.encodeToJsonElement(display ?: emptyList()).jsonArray,
                            disclosureMap = selectiveDisclosure,
                            type = type,
                        )
                    }
                }
            }
        }
    }

    private sealed interface IssuerSigningKey {
        data class Legacy(val key: Key) : IssuerSigningKey
        data class Crypto2(val key: Crypto2Key, val algorithm: JwsAlgorithm) : IssuerSigningKey
    }

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
