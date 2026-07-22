package id.walt.crypto2.jose

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

enum class JwkUse(val value: String) {
    SIGNATURE("sig"),
    ENCRYPTION("enc"),
}

enum class JwkOperation(val value: String) {
    SIGN("sign"),
    VERIFY("verify"),
    ENCRYPT("encrypt"),
    DECRYPT("decrypt"),
    WRAP_KEY("wrapKey"),
    UNWRAP_KEY("unwrapKey"),
    DERIVE_KEY("deriveKey"),
    DERIVE_BITS("deriveBits"),
}

data class JwkMetadata(
    val keyId: String? = null,
    val use: JwkUse? = null,
    val operations: Set<JwkOperation>? = null,
    val algorithm: String? = null,
)

object Jwk {
    private val json = Json {
        explicitNulls = true
        ignoreUnknownKeys = false
    }
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    fun parse(key: EncodedKey.Jwk): JsonObject = parse(key.data.toByteArray())

    fun parse(encoded: ByteArray): JsonObject =
        json.parseToJsonElement(encoded.decodeToString(throwOnInvalidSequence = true)) as? JsonObject
            ?: throw IllegalArgumentException("JWK must be a JSON object")

    fun containsPrivateMaterial(jwk: JsonObject): Boolean = when (requiredString(jwk, "kty")) {
        "EC", "OKP" -> "d" in jwk
        "RSA" -> listOf("d", "p", "q", "dp", "dq", "qi", "oth").any(jwk::containsKey)
        "oct" -> "k" in jwk
        else -> throw IllegalArgumentException("Unsupported JWK key type")
    }

    fun metadata(key: EncodedKey.Jwk): JwkMetadata {
        val jwk = parse(key)
        val operations = jwk["key_ops"]?.let { value ->
            val array = value as? JsonArray ?: throw IllegalArgumentException("JWK key_ops must be an array")
            val parsed = array.map { operation ->
                val primitive = operation as? JsonPrimitive
                    ?: throw IllegalArgumentException("JWK key operation must be a string")
                require(primitive.isString) { "JWK key operation must be a string" }
                val value = primitive.content
                JwkOperation.entries.firstOrNull { it.value == value }
                    ?: throw IllegalArgumentException("Unsupported JWK key operation: $value")
            }
            require(parsed.distinct().size == parsed.size) { "JWK key_ops must not contain duplicates" }
            parsed.toSet()
        }
        val use = optionalString(jwk, "use")?.let { value ->
            JwkUse.entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unsupported JWK use: $value")
        }
        validateUseAndOperations(use, operations)
        return JwkMetadata(
            keyId = optionalString(jwk, "kid"),
            use = use,
            operations = operations,
            algorithm = optionalString(jwk, "alg"),
        )
    }

    fun withMetadata(key: EncodedKey.Jwk, metadata: JwkMetadata): EncodedKey.Jwk {
        validateUseAndOperations(metadata.use, metadata.operations)
        val updated = parse(key).toMutableMap().apply {
            update("kid", metadata.keyId?.let(::JsonPrimitive))
            update("use", metadata.use?.value?.let(::JsonPrimitive))
            update(
                "key_ops",
                metadata.operations
                    ?.sortedBy { it.value }
                    ?.map { JsonPrimitive(it.value) }
                    ?.let(::JsonArray),
            )
            update("alg", metadata.algorithm?.let(::JsonPrimitive))
        }
        return EncodedKey.Jwk(
            data = BinaryData(json.encodeToString(JsonObject(updated)).encodeToByteArray()),
            privateMaterial = key.privateMaterial,
        )
    }

    suspend fun sha256Thumbprint(
        key: EncodedKey.Jwk,
        provider: CryptographyProvider = CryptographyProvider.Default,
    ): String {
        val jwk = parse(key)
        val keyType = requiredString(jwk, "kty")
        val requiredNames = when (keyType) {
            "EC" -> listOf("crv", "kty", "x", "y")
            "OKP" -> listOf("crv", "kty", "x")
            "RSA" -> listOf("e", "kty", "n")
            "oct" -> listOf("k", "kty")
            else -> throw IllegalArgumentException("Unsupported JWK key type")
        }
        if (keyType == "OKP") {
            val curve = requiredString(jwk, "crv")
            if (curve == "X25519" || curve == "X448") {
                validateMontgomeryCoordinate(curve, requiredString(jwk, "x"))
            }
        }
        val canonical = requiredNames.joinToString(separator = ",", prefix = "{", postfix = "}") { name ->
            val value = requiredString(jwk, name)
            require(value.all(::isThumbprintSafeAscii)) { "JWK thumbprint member contains unsupported characters" }
            when (name) {
                "e", "n" -> validateBase64Url(value, unsignedInteger = true)
                "k" -> validateBase64Url(value, unsignedInteger = false)
                "x", "y" -> validateBase64Url(
                    value,
                    unsignedInteger = false,
                    expectedSize = publicValueSize(keyType, requiredString(jwk, "crv")),
                )
            }
            "\"$name\":\"$value\""
        }
        val digest = provider.get(SHA256).hasher().hash(canonical.encodeToByteArray())
        return base64Url.encode(digest)
    }

    private fun MutableMap<String, JsonElement>.update(
        name: String,
        value: JsonElement?,
    ) {
        if (value == null) remove(name) else put(name, value)
    }

    private fun isThumbprintSafeAscii(character: Char): Boolean =
        character.code in 0x21..0x7e && character != '"' && character != '\\'

    private fun requiredString(jwk: JsonObject, name: String): String =
        optionalString(jwk, name) ?: throw IllegalArgumentException("JWK $name member is missing")

    private fun optionalString(jwk: JsonObject, name: String): String? = jwk[name]?.let { value ->
        val primitive = value as? JsonPrimitive ?: throw IllegalArgumentException("JWK $name member must be a string")
        require(primitive.isString) { "JWK $name member must be a string" }
        primitive.content
    }

    private fun validateBase64Url(value: String, unsignedInteger: Boolean, expectedSize: Int? = null) {
        require('=' !in value) { "JWK required members must use unpadded base64url" }
        val decoded = try {
            base64Url.decode(value)
        } catch (cause: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid JWK base64url member", cause)
        }
        require(decoded.isNotEmpty()) { "JWK base64url member cannot be empty" }
        require(expectedSize == null || decoded.size == expectedSize) { "JWK public-key member has an invalid size" }
        require(base64Url.encode(decoded) == value) { "JWK base64url member is not canonical" }
        if (unsignedInteger) {
            require(decoded.first() != 0.toByte()) { "JWK Base64urlUInt member has leading zero octets" }
        }
    }

    private fun validateUseAndOperations(use: JwkUse?, operations: Set<JwkOperation>?) {
        if (use == null || operations.isNullOrEmpty()) return
        val valid = when (use) {
            JwkUse.SIGNATURE -> operations.all { it == JwkOperation.SIGN || it == JwkOperation.VERIFY }
            JwkUse.ENCRYPTION -> operations.none { it == JwkOperation.SIGN || it == JwkOperation.VERIFY }
        }
        require(valid) { "JWK use and key_ops members are inconsistent" }
    }

    private fun publicValueSize(keyType: String, curve: String): Int? = when (keyType to curve) {
        "EC" to "P-256", "EC" to "secp256k1" -> 32
        "EC" to "P-384" -> 48
        "EC" to "P-521" -> 66
        "EC" to "brainpoolP256r1" -> 32
        "EC" to "brainpoolP384r1" -> 48
        "EC" to "brainpoolP512r1" -> 64
        "OKP" to "Ed25519", "OKP" to "X25519" -> 32
        "OKP" to "Ed448" -> 57
        "OKP" to "X448" -> 56
        else -> null
    }

    private fun validateMontgomeryCoordinate(curve: String, value: String) {
        val coordinate = base64Url.decode(value)
        val modulus = when (curve) {
            "X25519" -> ByteArray(32) { index ->
                when (index) {
                    0 -> 0xed.toByte()
                    31 -> 0x7f
                    else -> 0xff.toByte()
                }
            }
            "X448" -> ByteArray(56) { index -> if (index == 28) 0xfe.toByte() else 0xff.toByte() }
            else -> error("Unsupported Montgomery curve")
        }
        if (curve == "X25519") {
            require(coordinate.last().toInt() and 0x80 == 0) { "X25519 public key has a non-canonical high bit" }
        }
        require(compareLittleEndianUnsigned(coordinate, modulus) < 0) {
            "$curve public key is outside the canonical field range"
        }
    }

    private fun compareLittleEndianUnsigned(first: ByteArray, second: ByteArray): Int {
        require(first.size == second.size)
        for (index in first.lastIndex downTo 0) {
            val comparison = (first[index].toInt() and 0xff).compareTo(second[index].toInt() and 0xff)
            if (comparison != 0) return comparison
        }
        return 0
    }
}
