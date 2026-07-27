package id.walt.openid4vci.dpop

import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto.utils.ShaUtils
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.Instant

/** Default RFC 9449 DPoP proof verifier supporting ES256 with P-256 keys. */
class DefaultDPoPProofVerifier(
    private val proofMaxAgeSeconds: Long = DEFAULT_PROOF_MAX_AGE_SECONDS,
    private val clockSkewSeconds: Long = DEFAULT_CLOCK_SKEW_SECONDS,
    private val now: () -> Instant = { Clock.System.now() },
) : DPoPProofVerifier {

    private val crypto2Runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    init {
        require(proofMaxAgeSeconds > 0) { "DPoP proof maximum age must be positive" }
        require(clockSkewSeconds >= 0) { "DPoP clock skew must not be negative" }
    }

    override suspend fun verify(request: DPoPProofVerificationRequest): VerifiedDPoPProof {
        require(request.proofJwt.isNotBlank()) { "DPoP proof must not be blank" }
        require(request.method.isNotBlank()) { "DPoP request method must not be blank" }
        require(request.targetUri.isNotBlank()) { "DPoP target URI must not be blank" }

        val decoded = runCatching { request.proofJwt.decodeJws() }
            .getOrElse { throw IllegalArgumentException("Invalid DPoP proof JWT", it) }
        val algorithm = decoded.header.stringValue(JwtHeaderParams.ALGORITHM)
            ?: throw IllegalArgumentException("DPoP proof is missing ${JwtHeaderParams.ALGORITHM} header")

        require(decoded.header.stringValue(JwtHeaderParams.TYPE) == DPoPConstants.JWT_TYPE) {
            "DPoP proof ${JwtHeaderParams.TYPE} header must be ${DPoPConstants.JWT_TYPE}"
        }
        require(algorithm == DPoPConstants.ES256) {
            "Unsupported DPoP signing algorithm: $algorithm"
        }

        val jwk = decoded.header[JwtHeaderParams.JSON_WEB_KEY] as? JsonObject
            ?: throw IllegalArgumentException("DPoP proof is missing ${JwtHeaderParams.JSON_WEB_KEY} header")
        jwk.stringValue(JwtHeaderParams.ALGORITHM)?.let { jwkAlgorithm ->
            require(jwkAlgorithm == algorithm) { "DPoP JWK algorithm does not match the proof algorithm" }
        }
        jwk.stringValue(JWK_USE)?.let { use ->
            require(use == JWK_SIGNATURE_USE) { "DPoP JWK use must be $JWK_SIGNATURE_USE" }
        }
        jwk[JWK_KEY_OPERATIONS]?.let { value ->
            val operations = value as? JsonArray
                ?: throw IllegalArgumentException("DPoP JWK $JWK_KEY_OPERATIONS must be an array")
            val operationNames = operations.map { operation ->
                (operation as? JsonPrimitive)
                    ?.takeIf { it.isString && it.content.isNotBlank() }
                    ?.content
                    ?: throw IllegalArgumentException(
                        "DPoP JWK $JWK_KEY_OPERATIONS entries must be non-empty strings",
                    )
            }
            require(JWK_VERIFY_OPERATION in operationNames) {
                "DPoP JWK key_ops must allow verification"
            }
        }

        require(!Jwk.containsPrivateMaterial(jwk)) { "DPoP proof JWK must not contain private key material" }
        val encodedKey = EncodedKey.Jwk(
            data = BinaryData(Json.encodeToString(JsonObject.serializer(), jwk).encodeToByteArray()),
            privateMaterial = false,
        )
        val thumbprint = Jwk.sha256Thumbprint(encodedKey)
        val verificationKey = runCatching {
            crypto2Runtime.restore(
                encodedKey.toStoredSoftwareKey(KeyId(thumbprint), setOf(KeyUsage.VERIFY)),
            )
        }.getOrElse { throw IllegalArgumentException("DPoP proof contains an invalid JWK", it) }
        require(verificationKey.spec == KeySpec.Ec(EcCurve.P256)) {
            "DPoP proof key does not match algorithm $algorithm"
        }

        val verifiedPayload = runCatching {
            CompactJws.verify(request.proofJwt, verificationKey, setOf(JwsAlgorithm.ES256))
        }.getOrElse {
            throw IllegalArgumentException("Invalid DPoP proof signature", it)
        }.payload.decodeToString().let { payload ->
            runCatching { Json.parseToJsonElement(payload) }.getOrNull() as? JsonObject
                ?: throw IllegalArgumentException("DPoP proof payload must be a JSON object")
        }

        val jti = verifiedPayload.stringValue(JwtPayloadClaims.JWT_ID)
            ?: throw IllegalArgumentException("DPoP proof is missing ${JwtPayloadClaims.JWT_ID} claim")
        require(jti.length <= MAX_JTI_LENGTH) { "DPoP proof ${JwtPayloadClaims.JWT_ID} claim is too long" }

        val proofMethod = verifiedPayload.stringValue(DPoPConstants.HTTP_METHOD_CLAIM)
            ?: throw IllegalArgumentException("DPoP proof is missing ${DPoPConstants.HTTP_METHOD_CLAIM} claim")
        require(proofMethod == request.method) { "DPoP proof HTTP method does not match the request" }

        val proofUri = verifiedPayload.stringValue(DPoPConstants.HTTP_URI_CLAIM)
            ?: throw IllegalArgumentException("DPoP proof is missing ${DPoPConstants.HTTP_URI_CLAIM} claim")
        require(normalizeTargetUri(proofUri) == normalizeTargetUri(request.targetUri)) {
            "DPoP proof target URI does not match the request"
        }

        val issuedAt = verifiedPayload.longValue(JwtPayloadClaims.ISSUED_AT)
            ?: throw IllegalArgumentException("DPoP proof is missing ${JwtPayloadClaims.ISSUED_AT} claim")
        val currentTime = now().epochSeconds
        val earliest = currentTime - proofMaxAgeSeconds - clockSkewSeconds
        val latest = currentTime + clockSkewSeconds
        require(issuedAt in earliest..latest) { "DPoP proof is outside the accepted age window" }

        request.accessToken?.let { accessToken ->
            val accessTokenHash = verifiedPayload.stringValue(DPoPConstants.ACCESS_TOKEN_HASH_CLAIM)
                ?: throw IllegalArgumentException(
                    "DPoP proof is missing ${DPoPConstants.ACCESS_TOKEN_HASH_CLAIM} claim",
                )
            require(accessTokenHash == ShaUtils.calculateSha256Base64Url(accessToken)) {
                "DPoP proof access token hash does not match the presented access token"
            }
        }

        return VerifiedDPoPProof(thumbprint)
    }

    private fun normalizeTargetUri(value: String): NormalizedTargetUri {
        val url = runCatching { Url(value) }
            .getOrElse { throw IllegalArgumentException("Invalid DPoP target URI", it) }
        require(url.host.isNotBlank()) { "DPoP target URI must be absolute" }
        require(url.user == null && url.password == null) {
            "DPoP target URI must not contain user information"
        }
        return NormalizedTargetUri(
            scheme = url.protocol.name.lowercase(),
            host = url.host.lowercase(),
            port = url.port,
            path = normalizePercentEncoding(url.encodedPath.ifBlank { "/" }),
        )
    }

    private fun normalizePercentEncoding(value: String): String = buildString(value.length) {
        var index = 0
        while (index < value.length) {
            if (value[index] == '%' && index + 2 < value.length) {
                val encoded = value.substring(index + 1, index + 3)
                val decoded = encoded.toIntOrNull(16)?.toChar()
                if (decoded != null && decoded.isUnreservedUriCharacter()) {
                    append(decoded)
                } else {
                    append('%').append(encoded.uppercase())
                }
                index += 3
            } else {
                append(value[index])
                index++
            }
        }
    }

    private fun Char.isUnreservedUriCharacter(): Boolean =
        isLetterOrDigit() || this == '-' || this == '.' || this == '_' || this == '~'

    private fun JsonObject.stringValue(name: String): String? {
        val value = this[name] ?: return null
        return (value as? JsonPrimitive)
            ?.takeIf { it.isString && it.content.isNotBlank() }
            ?.content
            ?: throw IllegalArgumentException("DPoP value $name must be a non-empty string")
    }

    private fun JsonObject.longValue(name: String): Long? {
        val value = this[name] ?: return null
        return (value as? JsonPrimitive)
            ?.takeUnless { it.isString }
            ?.longOrNull
            ?: throw IllegalArgumentException("DPoP value $name must be an integer")
    }

    private data class NormalizedTargetUri(
        val scheme: String,
        val host: String,
        val port: Int,
        val path: String,
    )

    private companion object {
        const val DEFAULT_PROOF_MAX_AGE_SECONDS = 60L
        const val DEFAULT_CLOCK_SKEW_SECONDS = 15L
        const val MAX_JTI_LENGTH = 256
        const val JWK_USE = "use"
        const val JWK_SIGNATURE_USE = "sig"
        const val JWK_KEY_OPERATIONS = "key_ops"
        const val JWK_VERIFY_OPERATION = "verify"
    }
}
