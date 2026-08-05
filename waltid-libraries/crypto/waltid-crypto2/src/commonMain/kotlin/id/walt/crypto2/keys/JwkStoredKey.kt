package id.walt.crypto2.keys

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64

const val JWK_ALGORITHM_METADATA_KEY = "jwk.alg"

fun EncodedKey.Jwk.toStoredSoftwareKey(
    id: KeyId,
    usages: Set<KeyUsage>,
    metadata: Map<String, String> = emptyMap(),
): StoredKey.Software {
    require(usages.isNotEmpty()) { "JWK key usages cannot be empty" }
    val jwk = parseJwk()
    val spec = jwk.inferKeySpec()
    val containsPrivateMaterial = jwk.containsPrivateMaterial()
    require(privateMaterial == containsPrivateMaterial) { "JWK private-material metadata does not match contents" }
    val needsPrivateMaterial = usages.any { it in privateKeyUsages }
    require(containsPrivateMaterial || !needsPrivateMaterial) {
        "Public JWK cannot be used with private key usages"
    }
    require(needsPrivateMaterial || !containsPrivateMaterial) {
        "Persist the derived public JWK when no private key operation is permitted"
    }
    jwk.validateMetadata(usages)
    val declaredAlgorithm = jwk.optionalString("alg")
    val configuredAlgorithm = metadata[JWK_ALGORITHM_METADATA_KEY]
    configuredAlgorithm?.let { configured ->
        require(declaredAlgorithm == null || configured == declaredAlgorithm) {
            "Stored JWK algorithm metadata conflicts with the JWK alg member"
        }
    }
    spec.requireCompatibleJwkAlgorithm(declaredAlgorithm ?: configuredAlgorithm)
    return StoredKey.Software(
        version = StoredKey.CURRENT_VERSION,
        id = id,
        spec = spec,
        usages = usages,
        material = this,
        metadata = declaredAlgorithm?.let { metadata + (JWK_ALGORITHM_METADATA_KEY to it) } ?: metadata,
    )
}

internal fun KeySpec.requireCompatibleJwkAlgorithm(algorithm: String?) {
    if (algorithm == null) return
    // RFC 8812 requires a strict two-way binding between ES256K and secp256k1.
    val secp256k1 = this == KeySpec.Ec(EcCurve.SECP256K1)
    require(secp256k1 == (algorithm == "ES256K")) {
        "JWK algorithm $algorithm is incompatible with key specification $this"
    }
}

fun EncodedKey.Jwk.inferKeySpec(): KeySpec = parseJwk().inferKeySpec()

internal fun EncodedKey.Jwk.requirePublicJwk(expectedSpec: KeySpec) {
    require(!privateMaterial) { "Managed public JWK cannot contain private material" }
    val jwk = parseJwk()
    require(!jwk.containsPrivateMaterial()) { "Managed public JWK contains private or secret key parameters" }
    require(jwk.inferKeySpec() == expectedSpec) { "Managed public JWK does not match the key specification" }
}

internal fun EncodedKey.Jwk.parseJwk(): JsonObject =
    Json.parseToJsonElement(
        data.toByteArray().decodeToString(throwOnInvalidSequence = true)
    ) as? JsonObject ?: throw IllegalArgumentException("JWK must be a JSON object")

internal fun JsonObject.inferKeySpec(): KeySpec = when (requiredString("kty")) {
    "EC" -> {
        val curve = when (val name = requiredString("crv")) {
            "P-256" -> EcCurve.P256
            "P-384" -> EcCurve.P384
            "P-521" -> EcCurve.P521
            "secp256k1" -> EcCurve.SECP256K1
            "brainpoolP256r1" -> EcCurve.BRAINPOOL_P256R1
            "brainpoolP384r1" -> EcCurve.BRAINPOOL_P384R1
            "brainpoolP512r1" -> EcCurve.BRAINPOOL_P512R1
            else -> throw IllegalArgumentException("Unsupported JWK EC curve: $name")
        }
        val size = curve.publicValueSize()
        requiredBase64Url("x", size)
        requiredBase64Url("y", size)
        if ("d" in this) requiredBase64Url("d", size)
        KeySpec.Ec(curve)
    }
    "OKP" -> {
        val spec = when (val curve = requiredString("crv")) {
            "Ed25519" -> KeySpec.Edwards(EdwardsCurve.ED25519)
            "Ed448" -> KeySpec.Edwards(EdwardsCurve.ED448)
            "X25519" -> KeySpec.Montgomery(MontgomeryCurve.X25519)
            "X448" -> KeySpec.Montgomery(MontgomeryCurve.X448)
            else -> throw IllegalArgumentException("Unsupported JWK OKP curve: $curve")
        }
        val size = when (spec) {
            is KeySpec.Edwards -> spec.curve.publicValueSize()
            is KeySpec.Montgomery -> spec.curve.publicValueSize()
            else -> error("Unexpected OKP key specification")
        }
        requiredBase64Url("x", size)
        if ("d" in this) requiredBase64Url("d", size)
        spec
    }
    "RSA" -> {
        val modulus = requiredBase64UrlUInt("n")
        requiredBase64UrlUInt("e")
        if (containsPrivateMaterial()) {
            require("d" in this) { "RSA private JWK must contain d" }
            requiredBase64UrlUInt("d")
            val crtNames = listOf("p", "q", "dp", "dq", "qi")
            require(crtNames.none(::containsKey) || crtNames.all(::containsKey)) {
                "RSA private JWK CRT parameters must be complete"
            }
            crtNames.forEach { name ->
                if (name in this) requiredBase64UrlUInt(name)
            }
            this["oth"]?.let { value ->
                val others = value as? JsonArray ?: throw IllegalArgumentException("RSA JWK oth member must be an array")
                require(others.isNotEmpty()) { "RSA JWK oth member cannot be empty" }
                others.forEach { other ->
                    val parameter = other as? JsonObject
                        ?: throw IllegalArgumentException("RSA JWK oth parameters must be objects")
                    listOf("r", "d", "t").forEach(parameter::requiredBase64UrlUInt)
                }
            }
        }
        KeySpec.Rsa(modulus.bitLength())
    }
    "oct" -> KeySpec.Symmetric(
        family = when {
            optionalString("alg")?.startsWith("HS") == true -> SymmetricKeyType.HMAC
            optionalString("alg")?.contains("C20P") == true -> SymmetricKeyType.CHACHA20
            else -> SymmetricKeyType.AES
        },
        bits = requiredBase64Url("k").size * Byte.SIZE_BITS,
    )
    else -> throw IllegalArgumentException("Unsupported JWK key type: ${requiredString("kty")}")
}

private fun JsonObject.validateMetadata(usages: Set<KeyUsage>) {
    optionalString("kid")
    optionalString("alg")
    when (val use = optionalString("use")) {
        null -> Unit
        "sig" -> require(usages.all { it == KeyUsage.SIGN || it == KeyUsage.VERIFY }) {
            "Signature JWK cannot be used for encryption or agreement"
        }
        "enc" -> require(usages.none { it == KeyUsage.SIGN || it == KeyUsage.VERIFY }) {
            "Encryption JWK cannot be used for signatures"
        }
        else -> throw IllegalArgumentException("Unsupported JWK use: $use")
    }
    val keyOpsValue = this["key_ops"] ?: return
    val keyOps = keyOpsValue as? JsonArray ?: throw IllegalArgumentException("JWK key_ops must be an array")
    val operations = keyOps.map { value ->
        val primitive = value as? JsonPrimitive ?: throw IllegalArgumentException("JWK key operation must be a string")
        require(primitive.isString) { "JWK key operation must be a string" }
        primitive.content
    }
    require(operations.distinct().size == operations.size) { "JWK key_ops must not contain duplicates" }
    val allowed = operations.toSet()
    usages.forEach { usage ->
        val permitted = when (usage) {
            KeyUsage.SIGN -> "sign" in allowed
            KeyUsage.VERIFY -> "verify" in allowed
            KeyUsage.ENCRYPT -> "encrypt" in allowed
            KeyUsage.DECRYPT -> "decrypt" in allowed
            KeyUsage.KEY_AGREEMENT -> "deriveKey" in allowed || "deriveBits" in allowed
            KeyUsage.WRAP -> "wrapKey" in allowed
            KeyUsage.UNWRAP -> "unwrapKey" in allowed
        }
        require(permitted) { "JWK key_ops does not permit requested key usage: $usage" }
    }
}

internal fun JsonObject.containsPrivateMaterial(): Boolean = privateJwkParameterNames.any(::containsKey)

internal fun JsonObject.requiredString(name: String): String =
    optionalString(name)?.takeIf(String::isNotBlank)
        ?: throw IllegalArgumentException("JWK $name member is missing")

private fun JsonObject.optionalString(name: String): String? = this[name]?.let { value ->
    val primitive = value as? JsonPrimitive ?: throw IllegalArgumentException("JWK $name member must be a string")
    require(primitive.isString) { "JWK $name member must be a string" }
    primitive.content
}

internal fun JsonObject.requiredBase64Url(name: String, expectedSize: Int? = null): ByteArray {
    val value = requiredString(name)
    require('=' !in value) { "JWK members must use unpadded base64url" }
    val decoded = try {
        base64Url.decode(value)
    } catch (cause: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid JWK base64url member: $name", cause)
    }
    require(decoded.isNotEmpty()) { "JWK $name member cannot be empty" }
    require(expectedSize == null || decoded.size == expectedSize) { "JWK $name member has an invalid size" }
    require(base64Url.encode(decoded) == value) { "JWK $name member is not canonical base64url" }
    return decoded
}

internal fun JsonObject.requiredBase64UrlUInt(name: String): ByteArray = requiredBase64Url(name).also {
    require(it.first() != 0.toByte()) { "JWK $name Base64urlUInt member has leading zero octets" }
}

private fun ByteArray.bitLength(): Int =
    (size - 1) * Byte.SIZE_BITS + (Int.SIZE_BITS - (first().toInt() and 0xff).countLeadingZeroBits())

private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
internal fun ByteArray.encodeBase64Url(): String = base64Url.encode(this)
private val privateJwkParameterNames = setOf("d", "p", "q", "dp", "dq", "qi", "oth", "k")
private val privateKeyUsages = setOf(KeyUsage.SIGN, KeyUsage.DECRYPT, KeyUsage.KEY_AGREEMENT, KeyUsage.UNWRAP)

private fun EcCurve.publicValueSize(): Int = when (this) {
    EcCurve.P256, EcCurve.SECP256K1, EcCurve.BRAINPOOL_P256R1 -> 32
    EcCurve.P384, EcCurve.BRAINPOOL_P384R1 -> 48
    EcCurve.P521 -> 66
    EcCurve.BRAINPOOL_P512R1 -> 64
    else -> throw IllegalArgumentException("Unsupported JWK EC curve: $name")
}

private fun EdwardsCurve.publicValueSize(): Int = when (this) {
    EdwardsCurve.ED25519 -> 32
    EdwardsCurve.ED448 -> 57
    else -> throw IllegalArgumentException("Unsupported JWK Edwards curve: $name")
}

private fun MontgomeryCurve.publicValueSize(): Int = when (this) {
    MontgomeryCurve.X25519 -> 32
    MontgomeryCurve.X448 -> 56
    else -> throw IllegalArgumentException("Unsupported JWK Montgomery curve: $name")
}
