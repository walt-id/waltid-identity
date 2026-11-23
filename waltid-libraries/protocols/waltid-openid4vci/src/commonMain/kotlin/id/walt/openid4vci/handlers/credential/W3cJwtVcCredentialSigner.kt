package id.walt.openid4vci.handlers.credential

import id.walt.certificate.x509.X509Certificate
import id.walt.credentials.issuance.IssuanceHolderKey
import id.walt.credentials.issuance.MergingIssuer.mergingToVc
import id.walt.crypto.keys.Key
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.did.dids.DidUtils
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.proofs.VerifiedCredentialBinding
import id.walt.openid4vci.proofs.invalidCredentialProof
import id.walt.sdjwt.SDJwtVC.Companion.SD_JWT_VC_TYPE_HEADER
import id.walt.sdjwt.SDMap
import id.walt.w3c.CredentialBuilder
import id.walt.w3c.CredentialBuilderType
import id.walt.w3c.issuance.Issuer.getKidHeader
import id.walt.w3c.issuance.Issuer.kidForIssuer
import id.walt.w3c.vc.vcs.W3CVC
import id.walt.w3c.vc.vcs.applyIssuedV2Context
import kotlinx.coroutines.withContext
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
        val subjectDid = binding.holderDid.orEmpty()

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

        val vc = W3CVC(vcPayload)
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
        val (mappedVc, jwtOptions) = withContext(IssuanceHolderKey(binding.holderKey)) {
            w3cVc.mergingToVc(
                issuerId = issuerId,
                subjectDid = subjectDid,
                mappings = dataMapping ?: JsonObject(emptyMap()),
                display = Json.encodeToJsonElement(display ?: emptyList()).jsonArray,
            )
        }

        val issuerKid = when (issuerSigningKey) {
            is IssuerSigningKey.Legacy -> getKidHeader(issuerSigningKey.key, issuerId.takeIf(DidUtils::isDidUrl))
            is IssuerSigningKey.Crypto2 -> kidForIssuer(issuerSigningKey.key, issuerId)
        }
        if (selectiveDisclosure.isNullOrEmpty()) {
            return when (issuerSigningKey) {
                is IssuerSigningKey.Legacy -> mappedVc.signJws(
                    issuerKey = issuerSigningKey.key,
                    issuerId = issuerId,
                    issuerKid = issuerKid,
                    subjectDid = subjectDid,
                    additionalJwtHeader = additionalJwtHeaders,
                    additionalJwtOptions = jwtOptions,
                )
                is IssuerSigningKey.Crypto2 -> mappedVc.signJws(
                    issuerKey = issuerSigningKey.key,
                    algorithm = issuerSigningKey.algorithm,
                    issuerId = issuerId,
                    issuerKid = issuerKid,
                    subjectDid = subjectDid,
                    additionalJwtHeader = additionalJwtHeaders,
                    additionalJwtOptions = jwtOptions,
                )
            }
        }
        val type = if (builderType == CredentialBuilderType.W3CV2CredentialBuilder) SD_JWT_VC_TYPE_HEADER else "JWT"
        val sdJwtHeaders = additionalJwtHeaders + ("typ" to JsonPrimitive(type))
        return when (issuerSigningKey) {
            is IssuerSigningKey.Legacy -> mappedVc.signSdJwt(
                issuerKey = issuerSigningKey.key,
                issuerId = issuerId,
                issuerKid = issuerKid,
                subjectDid = subjectDid,
                disclosureMap = selectiveDisclosure,
                additionalJwtHeaders = sdJwtHeaders,
                additionalJwtOptions = jwtOptions,
            )
            is IssuerSigningKey.Crypto2 -> mappedVc.signSdJwt(
                issuerKey = issuerSigningKey.key,
                algorithm = issuerSigningKey.algorithm,
                issuerId = issuerId,
                issuerKid = issuerKid,
                subjectDid = subjectDid,
                disclosureMap = selectiveDisclosure,
                additionalJwtHeaders = sdJwtHeaders,
                additionalJwtOptions = jwtOptions,
            )
        }
    }

    private sealed interface IssuerSigningKey {
        data class Legacy(val key: Key) : IssuerSigningKey
        data class Crypto2(val key: Crypto2Key, val algorithm: JwsAlgorithm) : IssuerSigningKey
    }

    private const val JWT_HEADER_X5C = "x5c"
}
