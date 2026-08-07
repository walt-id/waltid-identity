package id.walt.certificate.x509

import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.RSAPadding
import at.asitplus.signum.supreme.SignatureResult
import at.asitplus.signum.supreme.sign.Signer
import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

actual suspend fun createCertificateTestKey(keyType: KeyType): Key {
    val signer = Signer.Ephemeral {
        when (keyType) {
            KeyType.RSA -> rsa { paddings = setOf(RSAPadding.PKCS1) }
            KeyType.secp256r1 -> ec { curve = ECCurve.SECP_256_R_1 }
            KeyType.secp384r1 -> ec { curve = ECCurve.SECP_384_R_1 }
            KeyType.secp521r1 -> ec { curve = ECCurve.SECP_521_R_1 }
            else -> error("Unsupported X.509 test key type on iOS: $keyType")
        }
    }.getOrThrow()

    return IosCertificateTestKey(signer, keyType)
}

private class IosCertificateTestKey(
    private val signer: Signer,
    override val keyType: KeyType,
    private val exposesPrivateKey: Boolean = true,
    private val keyId: String = Uuid.random().toString(),
) : Key() {
    override val hasPrivateKey: Boolean
        get() = exposesPrivateKey

    override suspend fun getKeyId(): String = keyId

    override suspend fun getThumbprint(): String = unsupported("thumbprint")

    override suspend fun exportJWK(): String = unsupported("JWK export")

    override suspend fun exportJWKObject(): JsonObject = unsupported("JWK export")

    override suspend fun exportPEM(): String {
        val base64 = Base64.encode(signer.publicKey.encodeToTlv().derEncoded).chunked(64).joinToString("\n")
        return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----"
    }

    override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): Any {
        check(customSignatureAlgorithm == null) { "Custom signature algorithms are not supported by X.509 test keys." }
        check(hasPrivateKey) { "Only private key can do signing." }
        val result = signer.sign(plaintext)
        check(result is SignatureResult.Success) { "Signing failed: $result" }
        return result.signature.rawByteArray
    }

    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String =
        unsupported("JWS signing")

    override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?,
        customSignatureAlgorithm: String?,
    ): Result<ByteArray> = Result.failure(UnsupportedOperationException("raw verification is not needed for X.509 tests"))

    override suspend fun verifyJws(signedJws: String): Result<JsonElement> =
        Result.failure(UnsupportedOperationException("JWS verification is not needed for X.509 tests"))

    override suspend fun getPublicKey(): Key =
        IosCertificateTestKey(signer, keyType, exposesPrivateKey = false, keyId = keyId)

    override suspend fun getPublicKeyRepresentation(): ByteArray = signer.publicKey.encodeToTlv().derEncoded

    override suspend fun getMeta(): KeyMeta = JwkKeyMeta(keyId)

    override suspend fun deleteKey(): Boolean = true
}

private fun <T> unsupported(operation: String): T =
    throw UnsupportedOperationException("$operation is not needed for X.509 tests")
