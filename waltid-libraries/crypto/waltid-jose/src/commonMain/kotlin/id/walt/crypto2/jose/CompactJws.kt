package id.walt.crypto2.jose

import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.JWK_ALGORITHM_METADATA_KEY
import id.walt.crypto2.keys.StorableKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

data class VerifiedJws(
    val protectedHeader: JsonObject,
    val payload: ByteArray,
    val algorithm: JwsAlgorithm,
)

data class UnverifiedJws(
    val protectedHeader: JsonObject,
    val payload: ByteArray,
    val algorithm: JwsAlgorithm,
)

class InvalidJwsSignatureException : IllegalArgumentException("Invalid JWS signature")

object CompactJws {
    private val json = Json {
        explicitNulls = true
        ignoreUnknownKeys = false
    }
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    suspend fun sign(
        payload: ByteArray,
        key: Key,
        algorithm: JwsAlgorithm,
        protectedHeader: JsonObject = JsonObject(emptyMap()),
    ): String {
        validateCallerHeader(protectedHeader, algorithm)
        validateKeySpec(key, algorithm)
        val signer = requireNotNull(key.capabilities.signer) { "Key does not support signing" }
        val header = JsonObject(
            buildMap {
                put("alg", JsonPrimitive(algorithm.identifier))
                protectedHeader.forEach { (name, value) ->
                    if (name != "alg") put(name, value)
                }
            },
        )
        validateSupportedHeaderSemantics(header)
        val encodedHeader = base64Url.encode(json.encodeToString(header).encodeToByteArray())
        val encodedPayload = base64Url.encode(payload)
        val signingInput = "$encodedHeader.$encodedPayload"
        val signature = signer.sign(signingInput.encodeToByteArray(), algorithm.toSignatureAlgorithm())
        validateSignatureLength(algorithm, signature)
        return "$signingInput.${base64Url.encode(signature)}"
    }

    suspend fun verify(
        compactJws: String,
        key: Key,
        allowedAlgorithms: Set<JwsAlgorithm>,
    ): VerifiedJws {
        val parsed = parse(compactJws)
        val algorithm = parsed.decoded.algorithm
        require(algorithm in allowedAlgorithms) { "JWS algorithm is not allowed" }
        validateKeySpec(key, algorithm)
        val verifier = requireNotNull(key.capabilities.verifier) { "Key does not support verification" }
        if (!verifier.verify(parsed.signingInput, parsed.signature, algorithm.toSignatureAlgorithm())) {
            throw InvalidJwsSignatureException()
        }
        return VerifiedJws(
            protectedHeader = parsed.decoded.protectedHeader,
            payload = parsed.decoded.payload,
            algorithm = algorithm,
        )
    }

    fun decodeUnverified(compactJws: String): UnverifiedJws = parse(compactJws).decoded

    suspend fun verify(
        compactJws: String,
        key: Key,
        expectedAlgorithm: JwsAlgorithm,
    ): VerifiedJws = verify(compactJws, key, setOf(expectedAlgorithm))

    private fun validateCallerHeader(header: JsonObject, algorithm: JwsAlgorithm) {
        header["alg"]?.let {
            require(it is JsonPrimitive && it.content == algorithm.identifier) {
                "Protected alg header conflicts with requested JWS algorithm"
            }
        }
    }

    private fun validateSupportedHeaderSemantics(header: JsonObject) {
        require("b64" !in header) { "The b64 protected header is not supported" }
        require("crit" !in header) { "Critical JWS headers are not supported" }
    }

    private fun validateKeySpec(key: Key, algorithm: JwsAlgorithm) {
        (key as? StorableKey)?.storedKey?.metadata?.get(JWK_ALGORITHM_METADATA_KEY)?.let { declared ->
            require(declared == algorithm.identifier) {
                "JWK algorithm $declared does not permit ${algorithm.identifier}"
            }
        }
        require(key.capabilities.supportsSignatureAlgorithm(algorithm.toSignatureAlgorithm())) {
            "Key does not support ${algorithm.identifier}"
        }
        require(key.spec.supportsJwsAlgorithm(algorithm)) {
            "Key specification ${key.spec} is incompatible with ${algorithm.identifier}"
        }
    }

    private fun validateSignatureLength(algorithm: JwsAlgorithm, signature: ByteArray) {
        val expectedLength = when (algorithm) {
            JwsAlgorithm.ES256, JwsAlgorithm.ES256K -> 64
            JwsAlgorithm.ES384 -> 96
            JwsAlgorithm.ES512 -> 132
            else -> return
        }
        require(signature.size == expectedLength) {
            "${algorithm.identifier} signature must be $expectedLength bytes"
        }
    }

    private fun parse(compactJws: String): ParsedJws {
        val parts = compactJws.split('.')
        require(parts.size == 3) { "Compact JWS must have exactly three parts" }
        require(parts[0].isNotEmpty()) { "JWS protected header cannot be empty" }
        require(parts[2].isNotEmpty()) { "JWS signature cannot be empty" }
        require(parts.none { '=' in it }) { "Compact JWS must use unpadded base64url" }
        val header = json.parseToJsonElement(
            base64Url.decode(parts[0]).decodeToString(throwOnInvalidSequence = true),
        ) as? JsonObject ?: throw IllegalArgumentException("JWS protected header must be a JSON object")
        validateSupportedHeaderSemantics(header)
        val algorithmHeader = header["alg"] as? JsonPrimitive
            ?: throw IllegalArgumentException("JWS alg header is missing or invalid")
        require(algorithmHeader.isString) { "JWS alg header must be a string" }
        val algorithm = JwsAlgorithm.parse(algorithmHeader.content)
        val signature = base64Url.decode(parts[2])
        validateSignatureLength(algorithm, signature)
        return ParsedJws(
            decoded = UnverifiedJws(
                protectedHeader = header,
                payload = base64Url.decode(parts[1]),
                algorithm = algorithm,
            ),
            signingInput = "${parts[0]}.${parts[1]}".encodeToByteArray(),
            signature = signature,
        )
    }

    private data class ParsedJws(
        val decoded: UnverifiedJws,
        val signingInput: ByteArray,
        val signature: ByteArray,
    )
}
