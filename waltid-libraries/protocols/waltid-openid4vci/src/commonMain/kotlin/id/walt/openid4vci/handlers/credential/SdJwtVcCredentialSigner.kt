package id.walt.openid4vci.handlers.credential

import id.walt.certificate.x509.X509Certificate
import id.walt.crypto.keys.Key
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.did.dids.DidUtils
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.proofs.VerifiedCredentialBinding
import id.walt.openid4vci.proofs.invalidCredentialProof
import id.walt.sdjwt.SDJwt
import id.walt.sdjwt.SDJwt.Companion.SEPARATOR_STR
import id.walt.sdjwt.SDJwtVC
import id.walt.sdjwt.SDJwtVC.Companion.SD_JWT_VC_TYPE_HEADER
import id.walt.sdjwt.SDJwtVC.Companion.defaultPayloadProperties
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.SDPayload
import id.walt.w3c.issuance.Issuer.getKidHeader
import id.walt.w3c.issuance.dataFunctions
import id.walt.w3c.utils.CredentialDataMergeUtils.mergeSDJwtVCPayloadWithMapping
import kotlinx.serialization.json.*
import id.walt.crypto2.keys.Key as Crypto2Key

/** Generates credentials from an explicitly supplied binding. This signer rejects null bindings. */
object SdJwtVcCredentialSigner {
    @Deprecated("Use the Crypto2Key overload")
    suspend fun generateSdJwtVC(
        verifiedBinding: VerifiedCredentialBinding?,
        credentialData: JsonObject,
        issuerKey: Key,
        issuerId: String,
        vct: String,
        selectiveDisclosure: SDMap? = null,
        dataMapping: JsonObject? = null,
        x5Chain: List<X509Certificate>? = null,
        display: List<CredentialDisplay>? = null,
        sdJwtTypeHeader: String? = null,
        sdJwtCredentialClaims: JsonObject? = null,
    ): String = generateSdJwtVC(
        credentialData = credentialData,
        issuerSigningKey = IssuerSigningKey.Legacy(issuerKey),
        issuerId = issuerId,
        vct = vct,
        selectiveDisclosure = selectiveDisclosure,
        dataMapping = dataMapping,
        x5Chain = x5Chain,
        display = display,
        sdJwtTypeHeader = sdJwtTypeHeader,
        sdJwtCredentialClaims = sdJwtCredentialClaims,
        verifiedBinding = verifiedBinding,
    )

    suspend fun generateSdJwtVC(
        verifiedBinding: VerifiedCredentialBinding?,
        credentialData: JsonObject,
        issuerKey: Crypto2Key,
        algorithm: JwsAlgorithm,
        issuerId: String,
        vct: String,
        selectiveDisclosure: SDMap? = null,
        dataMapping: JsonObject? = null,
        x5Chain: List<X509Certificate>? = null,
        display: List<CredentialDisplay>? = null,
        sdJwtTypeHeader: String? = null,
        sdJwtCredentialClaims: JsonObject? = null,
    ): String = generateSdJwtVC(
        credentialData = credentialData,
        issuerSigningKey = IssuerSigningKey.Crypto2(issuerKey, algorithm),
        issuerId = issuerId,
        vct = vct,
        selectiveDisclosure = selectiveDisclosure,
        dataMapping = dataMapping,
        x5Chain = x5Chain,
        display = display,
        sdJwtTypeHeader = sdJwtTypeHeader,
        sdJwtCredentialClaims = sdJwtCredentialClaims,
        verifiedBinding = verifiedBinding,
    )

    private suspend fun generateSdJwtVC(
        verifiedBinding: VerifiedCredentialBinding?,
        credentialData: JsonObject,
        issuerSigningKey: IssuerSigningKey,
        issuerId: String,
        vct: String,
        selectiveDisclosure: SDMap?,
        dataMapping: JsonObject?,
        x5Chain: List<X509Certificate>?,
        display: List<CredentialDisplay>?,
        sdJwtTypeHeader: String?,
        sdJwtCredentialClaims: JsonObject?,
    ): String {
        val binding = verifiedBinding
            ?: throw invalidCredentialProof("SD-JWT VC issuance requires a verified credential binding")
        val holderKeyJson = exportHolderJwk(binding.holderKey)
        val holderDid = binding.holderDid

        val sdPayload = SDPayload.createSDPayload(
            fullPayload = credentialData.mergeSDJwtVCPayloadWithMapping(
                mapping = dataMapping ?: JsonObject(emptyMap()),
                context = mapOf(
                    "subjectDid" to holderDid,
                    "issuerDid" to issuerId,
                    "issuerId" to issuerId,
                    "display" to Json.encodeToJsonElement(display ?: emptyList()).jsonArray,
                ).filterValues {
                    when (it) {
                        is JsonElement -> it !is JsonNull && (it !is JsonObject || it.jsonObject.isNotEmpty()) && (it !is JsonArray || it.jsonArray.isNotEmpty())
                        else -> it.toString().isNotEmpty()
                    }
                }.mapValues { (_, value) ->
                    when (value) {
                        is JsonElement -> value
                        else -> JsonPrimitive(value.toString())
                    }
                },
                data = dataFunctions
            ),
            disclosureMap = selectiveDisclosure ?: SDMap(mapOf())
        )

        val defaultPayloadProperties = defaultPayloadProperties(
            issuerId = issuerId,
            cnf = buildJsonObject {
                put("jwk", holderKeyJson)
            },
            vct = vct
        ).let { defPayloadProps ->
            display?.takeIf { it.isNotEmpty() }?.let { dis ->
                defPayloadProps.plus(
                    "display" to Json.encodeToJsonElement(dis)
                )
            } ?: defPayloadProps
        }


        val extraClaims = sdJwtCredentialClaims ?: emptyMap()
        val undisclosedPayload =
            sdPayload.undisclosedPayload.plus(defaultPayloadProperties).plus(extraClaims).let { JsonObject(it) }

        val fullPayload = sdPayload.fullPayload.plus(defaultPayloadProperties).plus(extraClaims).let { JsonObject(it) }

        val issuerDid = if (DidUtils.isDidUrl(issuerId)) issuerId else null

        val issuerKid = when (issuerSigningKey) {
            is IssuerSigningKey.Legacy -> getKidHeader(issuerSigningKey.key, issuerDid)
            is IssuerSigningKey.Crypto2 -> getKidHeader(issuerSigningKey.key, issuerDid)
        }
        val headers = mapOf(
            JWT_HEADER_KID to JsonPrimitive(issuerKid),
            JWT_HEADER_TYPE to JsonPrimitive(sdJwtTypeHeader ?: SD_JWT_VC_TYPE_HEADER),
        ).plus(x5Chain?.let {
            mapOf(JWT_HEADER_X5C to JsonArray(it.map { cert -> JsonPrimitive(cert.encodedDer.toByteArray().encodeToBase64()) }))
        } ?: mapOf())

        val finalSdPayload = SDPayload.createSDPayload(
            fullPayload = fullPayload,
            undisclosedPayload = undisclosedPayload
        )

        val signable = finalSdPayload.undisclosedPayload.toString().encodeToByteArray()
        val jwt = when (issuerSigningKey) {
            is IssuerSigningKey.Legacy -> issuerSigningKey.key.signJws(signable, headers)
            is IssuerSigningKey.Crypto2 -> CompactJws.sign(
                payload = signable,
                key = issuerSigningKey.key,
                algorithm = issuerSigningKey.algorithm,
                protectedHeader = JsonObject(headers),
            )
        }

        val sdJwtVC = SDJwtVC(
            sdJwt = SDJwt.createFromSignedJwt(
                signedJwt = jwt,
                sdPayload = finalSdPayload
            )
        )

        return sdJwtVC.toString().plus(SEPARATOR_STR)
    }

    private suspend fun exportHolderJwk(holderKey: Crypto2Key): JsonObject =
        holderKey.exportPublicJwkObject().plus(
            JWT_HEADER_KID to holderKey.id.value.toJsonElement(),
        ).toJsonObject()

    private sealed interface IssuerSigningKey {
        data class Legacy(val key: Key) : IssuerSigningKey
        data class Crypto2(val key: Crypto2Key, val algorithm: JwsAlgorithm) : IssuerSigningKey
    }

    private const val JWT_HEADER_KID = "kid"
    private const val JWT_HEADER_TYPE = "typ"
    private const val JWT_HEADER_X5C = "x5c"

}
