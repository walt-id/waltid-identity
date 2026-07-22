package id.walt.openid4vci.handlers.credential

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.credentials.keyresolver.resolvers.DidKeyResolver
import id.walt.did.dids.DidUtils
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.requests.credential.CredentialRequest
import id.walt.sdjwt.SDJwt
import id.walt.sdjwt.SDJwtVC
import id.walt.sdjwt.SDJwtVC.Companion.SD_JWT_VC_TYPE_HEADER
import id.walt.sdjwt.SDJwtVC.Companion.defaultPayloadProperties
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.SDPayload
import id.walt.w3c.issuance.Issuer.getKidHeader
import id.walt.w3c.utils.CredentialDataMergeUtils.mergeSDJwtVCPayloadWithMapping
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.sdjwt.SDJwt.Companion.SEPARATOR_STR
import id.walt.w3c.issuance.dataFunctions
import id.walt.x509.CertificateDer
import kotlinx.serialization.json.jsonObject

object SdJwtVcCredentialSigner {
    @Deprecated("Use the Crypto2Key overload")
    suspend fun generateSdJwtVC(
        credentialRequest: CredentialRequest,
        credentialData: JsonObject,
        issuerKey: Key,
        issuerId: String,
        vct: String,
        selectiveDisclosure: SDMap? = null,
        dataMapping: JsonObject? = null,
        x5Chain: List<CertificateDer>? = null,
        display: List<CredentialDisplay>? = null,
        sdJwtTypeHeader: String? = null,
        sdJwtCredentialClaims: JsonObject? = null,
    ): String = generateSdJwtVC(
        credentialRequest = credentialRequest,
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
    )

    suspend fun generateSdJwtVC(
        credentialRequest: CredentialRequest,
        credentialData: JsonObject,
        issuerKey: Crypto2Key,
        algorithm: JwsAlgorithm,
        issuerId: String,
        vct: String,
        selectiveDisclosure: SDMap? = null,
        dataMapping: JsonObject? = null,
        x5Chain: List<CertificateDer>? = null,
        display: List<CredentialDisplay>? = null,
        sdJwtTypeHeader: String? = null,
        sdJwtCredentialClaims: JsonObject? = null,
    ): String = generateSdJwtVC(
        credentialRequest = credentialRequest,
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
    )

    private suspend fun generateSdJwtVC(
        credentialRequest: CredentialRequest,
        credentialData: JsonObject,
        issuerSigningKey: IssuerSigningKey,
        issuerId: String,
        vct: String,
        selectiveDisclosure: SDMap?,
        dataMapping: JsonObject?,
        x5Chain: List<CertificateDer>?,
        display: List<CredentialDisplay>?,
        sdJwtTypeHeader: String?,
        sdJwtCredentialClaims: JsonObject?,
    ): String {
        val proofHeader = credentialRequest.proofs?.jwt?.let { JwtUtils.parseJWTHeader(it.first()) }
            ?: throw IllegalArgumentException("Missing JWT proof in proofs")
        val holderKeyJson = resolveHolderJwk(proofHeader)

        val holderDid = proofHeader[JWT_HEADER_KID]?.jsonPrimitive?.content.let {
            if (!it.isNullOrEmpty() && DidUtils.isDidUrl(it)) it.substringBefore("#") else null
        }

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
        val undisclosedPayload = sdPayload.undisclosedPayload.plus(defaultPayloadProperties).plus(extraClaims).let { JsonObject(it) }

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
            mapOf(JWT_HEADER_X5C to JsonArray(it.map { cert -> JsonPrimitive(cert.bytes.toByteArray().encodeToBase64()) }))
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

    private suspend fun resolveHolderJwk(proofHeader: JsonObject): JsonObject {
        val holderKey = when {
            JWT_HEADER_JWK in proofHeader ->
                JWKKey.importJWK(requireNotNull(proofHeader[JWT_HEADER_JWK]).toString()).getOrThrow().getPublicKey()

            JWT_HEADER_KID in proofHeader -> {
                val holderKid = requireNotNull(proofHeader[JWT_HEADER_KID]?.jsonPrimitive).content
                require(DidUtils.isDidUrl(holderKid))
                DidKeyResolver.resolveKeyFromDid(holderKid.substringBefore("#"), holderKid)
            }

            else -> throw IllegalArgumentException("Proof JWT header must contain kid or jwk claim")
        }
        return holderKey.exportJWKObject().plus(
            JWT_HEADER_KID to holderKey.getKeyId().toJsonElement(),
        ).toJsonObject()
    }

    private sealed interface IssuerSigningKey {
        data class Legacy(val key: Key) : IssuerSigningKey
        data class Crypto2(val key: Crypto2Key, val algorithm: JwsAlgorithm) : IssuerSigningKey
    }

    private const val JWT_HEADER_KID = "kid"
    private const val JWT_HEADER_JWK = "jwk"
    private const val JWT_HEADER_TYPE = "typ"
    private const val JWT_HEADER_X5C = "x5c"

}
