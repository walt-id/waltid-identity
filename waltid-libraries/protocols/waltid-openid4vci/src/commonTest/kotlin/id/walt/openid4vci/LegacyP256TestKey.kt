package id.walt.openid4vci

import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key as LegacyKey
import id.walt.crypto.keys.KeyType
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.jose.*
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64

/** Exercises legacy signing APIs with a real software key, without requiring the iOS Keychain. */
internal class LegacyP256TestKey(private val key: Key) : LegacyKey() {
    init { require(key.spec == KeySpec.Ec(EcCurve.P256)) }

    override val keyType = KeyType.secp256r1
    override val hasPrivateKey get() = key.capabilities.signer != null
    override suspend fun getKeyId() = key.id.value
    override suspend fun getThumbprint() = Jwk.sha256Thumbprint(key.exportPublicJwk())
    override suspend fun getMeta() = JwkKeyMeta(getKeyId())
    override suspend fun deleteKey(): Boolean = error("Test keys are not persisted")

    override suspend fun exportJWKObject(): JsonObject = if (hasPrivateKey) {
        Jwk.parse(requireNotNull(key.capabilities.privateKeyExporter).exportPrivateKey().toPrivateJwk(key.spec))
    } else key.exportPublicJwkObject()

    override suspend fun exportJWK() = exportJWKObject().toString()

    override suspend fun getPublicKey(): LegacyKey = LegacyP256TestKey(
        CryptoRuntime(defaultSoftwareKeyProviders()).restore(
            key.exportPublicJwk().toStoredSoftwareKey(key.id, setOf(KeyUsage.VERIFY)),
        ),
    )

    override suspend fun getPublicKeyRepresentation(): ByteArray {
        val jwk = key.exportPublicJwkObject()
        val decoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
        return byteArrayOf(4) + decoder.decode(jwk.getValue("x").jsonPrimitive.content) +
            decoder.decode(jwk.getValue("y").jsonPrimitive.content)
    }

    override suspend fun exportPEM(): String {
        val der = key.exportPublicJwk().toSpkiDer(key.spec).data.toByteArray()
        return "-----BEGIN PUBLIC KEY-----\n" + Base64.encode(der).chunked(64).joinToString("\n") +
            "\n-----END PUBLIC KEY-----"
    }

    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String =
        CompactJws.sign(plaintext, key, JwsAlgorithm.ES256, JsonObject(headers))

    override suspend fun verifyJws(signedJws: String): Result<JsonElement> = runCatching {
        Json.parseToJsonElement(CompactJws.verify(signedJws, key, JwsAlgorithm.ES256).payload.decodeToString())
    }

    override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray =
        requireNotNull(key.capabilities.signer).sign(plaintext, rawAlgorithm(customSignatureAlgorithm))

    override suspend fun verifyRaw(
        signed: ByteArray, detachedPlaintext: ByteArray?, customSignatureAlgorithm: String?,
    ): Result<ByteArray> = runCatching {
        val data = requireNotNull(detachedPlaintext)
        require(requireNotNull(key.capabilities.verifier).verify(data, signed, rawAlgorithm(customSignatureAlgorithm)))
        data
    }

    private fun rawAlgorithm(custom: String?): SignatureAlgorithm {
        require(custom == null || custom == "SHA256withECDSA")
        // Legacy COSE adapters consume DER and convert it to the COSE signature encoding.
        return SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
    }
}
