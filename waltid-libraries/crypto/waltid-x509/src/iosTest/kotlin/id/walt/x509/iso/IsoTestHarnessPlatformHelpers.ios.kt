package id.walt.x509.iso

import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.supreme.SignatureResult
import at.asitplus.signum.supreme.sign.Signer
import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

actual fun isBigIntegerZero(bigInt: ByteString): Boolean {
    return bigInt.toByteArray().all { it == 0.toByte() }
}

actual fun isBigIntegerPositive(bigInt: ByteString): Boolean {
    val bytes = bigInt.toByteArray()
    if (bytes.isEmpty()) return false
    return (bytes[0].toInt() and 0x80) == 0 && bytes.any { it != 0.toByte() }
}

actual suspend fun createIsoTestKey(
    keyType: KeyType,
    hasPrivateKey: Boolean,
): Key =
    when (val curve = keyType.toSignumCurveOrNull()) {
        null -> UnsupportedIsoTestKey(keyType, hasPrivateKey)
        else -> Signer.Ephemeral {
            ec {
                this.curve = curve
            }
        }.getOrThrow().let { signer ->
            IosEphemeralIsoTestKey(
                signer = signer,
                keyType = keyType,
                exposesPrivateKey = hasPrivateKey,
            )
        }
    }

private class IosEphemeralIsoTestKey(
    private val signer: Signer,
    override val keyType: KeyType,
    private val exposesPrivateKey: Boolean,
    private val keyId: String = Uuid.random().toString(),
) : Key() {
    override val hasPrivateKey: Boolean
        get() = exposesPrivateKey

    override suspend fun getKeyId(): String = keyId

    override suspend fun getThumbprint(): String = keyId

    override suspend fun exportJWK(): String =
        unsupported("JWK export")

    override suspend fun exportJWKObject(): JsonObject =
        unsupported("JWK export")

    override suspend fun exportPEM(): String =
        unsupported("PEM export")

    override suspend fun signRaw(
        plaintext: ByteArray,
        customSignatureAlgorithm: String?,
    ): Any {
        check(hasPrivateKey) { "Only private key can do signing." }
        val result = signer.sign(plaintext)
        check(result is SignatureResult.Success) { "Signing failed: $result" }
        return result.signature.rawByteArray
    }

    override suspend fun signJws(
        plaintext: ByteArray,
        headers: Map<String, JsonElement>,
    ): String =
        unsupported("JWS signing")

    override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?,
        customSignatureAlgorithm: String?,
    ): Result<ByteArray> =
        Result.failure(UnsupportedOperationException("raw verification is not needed for ISO X.509 tests"))

    override suspend fun verifyJws(signedJws: String): Result<JsonElement> =
        Result.failure(UnsupportedOperationException("JWS verification is not needed for ISO X.509 tests"))

    override suspend fun getPublicKey(): Key =
        IosEphemeralIsoTestKey(
            signer = signer,
            keyType = keyType,
            exposesPrivateKey = false,
            keyId = keyId,
        )

    override suspend fun getPublicKeyRepresentation(): ByteArray =
        signer.publicKey.encodeToTlv().derEncoded

    override suspend fun getMeta(): KeyMeta = JwkKeyMeta(keyId)

    override suspend fun deleteKey(): Boolean = true
}

private class UnsupportedIsoTestKey(
    override val keyType: KeyType,
    override val hasPrivateKey: Boolean,
) : Key() {
    override suspend fun getKeyId(): String = "unsupported-test-key-$keyType"
    override suspend fun getThumbprint(): String = getKeyId()
    override suspend fun exportJWK(): String = unsupported("JWK export")
    override suspend fun exportJWKObject(): JsonObject = unsupported("JWK export")
    override suspend fun exportPEM(): String = unsupported("PEM export")
    override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): Any =
        unsupported("raw signing")
    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String =
        unsupported("JWS signing")
    override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?,
        customSignatureAlgorithm: String?,
    ): Result<ByteArray> =
        Result.failure(UnsupportedOperationException("raw verification is not needed for unsupported ISO test keys"))
    override suspend fun verifyJws(signedJws: String): Result<JsonElement> =
        Result.failure(UnsupportedOperationException("JWS verification is not needed for unsupported ISO test keys"))
    override suspend fun getPublicKey(): Key = UnsupportedIsoTestKey(keyType, hasPrivateKey = false)
    override suspend fun getPublicKeyRepresentation(): ByteArray = ByteArray(0)
    override suspend fun getMeta(): KeyMeta = JwkKeyMeta(getKeyId())
    override suspend fun deleteKey(): Boolean = true
}

private fun KeyType.toSignumCurveOrNull(): ECCurve? =
    when (this) {
        KeyType.secp256r1 -> ECCurve.SECP_256_R_1
        KeyType.secp384r1 -> ECCurve.SECP_384_R_1
        KeyType.secp521r1 -> ECCurve.SECP_521_R_1
        else -> null
    }

private fun <T> unsupported(operation: String): T =
    throw UnsupportedOperationException("$operation is not needed for ISO X.509 tests")
