@file:OptIn(
    dev.whyoleg.cryptography.CryptographyProviderApi::class,
    dev.whyoleg.cryptography.DelicateCryptographyApi::class,
)

package id.walt.crypto2.hpke

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.HpkeAeadId
import id.walt.crypto2.keys.HpkeCiphertext
import id.walt.crypto2.keys.HpkeKdfId
import id.walt.crypto2.keys.HpkeKemId
import id.walt.crypto2.keys.HpkeSuite
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.publicOnly
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

object Hpke {
    val P256_HKDF_SHA256_AES_128_GCM = HpkeSuite(
        kem = HpkeKemId.DHKEM_P256_HKDF_SHA256,
        kdf = HpkeKdfId.HKDF_SHA256,
        aead = HpkeAeadId.AES_128_GCM,
    )

    private const val HASH_SIZE = 32
    private const val KEY_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val TAG_SIZE = 16
    private val versionLabel = "HPKE-v1".encodeToByteArray()
    private val kemSuiteId = "KEM".encodeToByteArray() + i2osp(KEM_ID)
    private val hpkeSuiteId = "HPKE".encodeToByteArray() + i2osp(KEM_ID) + i2osp(KDF_ID) + i2osp(AEAD_ID)
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    private val json = Json { explicitNulls = true }

    suspend fun openBase(
        recipientKey: Key,
        ciphertext: HpkeCiphertext,
        info: ByteArray = byteArrayOf(),
        aad: ByteArray = byteArrayOf(),
        provider: CryptographyProvider = CryptographyProvider.Default,
    ): ByteArray {
        require(ciphertext.suite == P256_HKDF_SHA256_AES_128_GCM) { "Unsupported HPKE suite" }
        require(recipientKey.spec == KeySpec.Ec(EcCurve.P256)) { "HPKE recipient key must use P-256" }
        require(KeyUsage.KEY_AGREEMENT in recipientKey.usages) { "HPKE recipient key does not permit key agreement" }
        require(recipientKey.capabilities.supportsKeyAgreementAlgorithm(KeyAgreementAlgorithm.Ecdh)) {
            "HPKE recipient key does not support ECDH"
        }
        val agreement = requireNotNull(recipientKey.capabilities.keyAgreement) {
            "HPKE recipient key cannot perform key agreement"
        }
        val encapsulatedKey = ciphertext.encapsulatedKey.toByteArray()
        val ephemeralPublicJwk = decodeEphemeralPublicKey(encapsulatedKey, provider)
        val recipientPublicJwk = requireNotNull(recipientKey.capabilities.publicKeyExporter) {
            "HPKE recipient key does not export its public key"
        }.exportPublicKey().toPublicJwk(recipientKey.spec, provider).publicOnly()
        val recipientPublicKey = rawP256PublicKey(recipientPublicJwk)
        val dh = agreement.generateSharedSecret(ephemeralPublicJwk, KeyAgreementAlgorithm.Ecdh).toByteArray()
        require(dh.size == HASH_SIZE) { "P-256 ECDH shared secret must be 32 bytes" }
        val context = deriveContext(dh, encapsulatedKey, recipientPublicKey, info, provider)
        val encrypted = ciphertext.ciphertext.toByteArray()
        require(encrypted.size >= TAG_SIZE) { "HPKE ciphertext must include a 128-bit authentication tag" }
        val aesKey = provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, context.key)
        return aesKey.cipher().decryptWithIv(context.baseNonce, encrypted, aad)
    }

    suspend fun sealBase(
        recipientPublicKey: EncodedKey.Jwk,
        plaintext: ByteArray,
        info: ByteArray = byteArrayOf(),
        aad: ByteArray = byteArrayOf(),
        provider: CryptographyProvider = CryptographyProvider.Default,
    ): HpkeCiphertext {
        require(!recipientPublicKey.privateMaterial) { "HPKE recipient key must contain public material only" }
        validatePublicJwkMetadata(recipientPublicKey)
        val recipientRaw = rawP256PublicKey(recipientPublicKey)
        val ecdh = provider.get(ECDH)
        val recipient = ecdh.publicKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PublicKey.Format.RAW, recipientRaw)
        val ephemeral = ecdh.keyPairGenerator(EC.Curve.P256).generateKey()
        val encapsulatedKey = ephemeral.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW)
        val dh = ephemeral.privateKey.sharedSecretGenerator().generateSharedSecretToByteArray(recipient)
        require(dh.size == HASH_SIZE) { "P-256 ECDH shared secret must be 32 bytes" }
        val context = deriveContext(dh, encapsulatedKey, recipientRaw, info, provider)
        val aesKey = provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, context.key)
        return HpkeCiphertext(
            suite = P256_HKDF_SHA256_AES_128_GCM,
            encapsulatedKey = BinaryData(encapsulatedKey),
            ciphertext = BinaryData(aesKey.cipher().encryptWithIv(context.baseNonce, plaintext, aad)),
        )
    }

    private suspend fun deriveContext(
        dh: ByteArray,
        encapsulatedKey: ByteArray,
        recipientPublicKey: ByteArray,
        info: ByteArray,
        provider: CryptographyProvider,
    ): Context {
        val eaePrk = labeledExtract(byteArrayOf(), "eae_prk", dh, kemSuiteId, provider)
        val sharedSecret = labeledExpand(
            eaePrk,
            "shared_secret",
            encapsulatedKey + recipientPublicKey,
            HASH_SIZE,
            kemSuiteId,
            provider,
        )
        val pskIdHash = labeledExtract(byteArrayOf(), "psk_id_hash", byteArrayOf(), hpkeSuiteId, provider)
        val infoHash = labeledExtract(byteArrayOf(), "info_hash", info, hpkeSuiteId, provider)
        val keyScheduleContext = byteArrayOf(MODE_BASE) + pskIdHash + infoHash
        val secret = labeledExtract(sharedSecret, "secret", byteArrayOf(), hpkeSuiteId, provider)
        return Context(
            key = labeledExpand(secret, "key", keyScheduleContext, KEY_SIZE, hpkeSuiteId, provider),
            baseNonce = labeledExpand(secret, "base_nonce", keyScheduleContext, NONCE_SIZE, hpkeSuiteId, provider),
        )
    }

    private suspend fun decodeEphemeralPublicKey(
        encoded: ByteArray,
        provider: CryptographyProvider,
    ): EncodedKey.Jwk {
        require(encoded.size == 65 && encoded.first() == 0x04.toByte()) {
            "P-256 HPKE encapsulated key must be an uncompressed 65-byte point"
        }
        val publicKey = provider.get(ECDH)
            .publicKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PublicKey.Format.RAW, encoded)
        require(publicKey.encodeToByteArray(EC.PublicKey.Format.RAW).contentEquals(encoded)) {
            "HPKE encapsulated key is not a canonical P-256 point"
        }
        val parsed = json.parseToJsonElement(
            publicKey.encodeToByteArray(EC.PublicKey.Format.JWK).decodeToString()
        ) as? JsonObject ?: throw IllegalArgumentException("HPKE ephemeral JWK must be an object")
        return EncodedKey.Jwk(
            data = BinaryData(
                json.encodeToString(
                    JsonObject(parsed.filterKeys { it == "kty" || it == "crv" || it == "x" || it == "y" })
                ).encodeToByteArray()
            ),
            privateMaterial = false,
        )
    }

    private fun rawP256PublicKey(jwk: EncodedKey.Jwk): ByteArray {
        val parsed = json.parseToJsonElement(jwk.data.toByteArray().decodeToString()) as? JsonObject
            ?: throw IllegalArgumentException("HPKE recipient JWK must be an object")
        require(parsed["kty"]?.jsonPrimitive?.content == "EC" && parsed["crv"]?.jsonPrimitive?.content == "P-256") {
            "HPKE recipient public key must use P-256"
        }
        val x = decodeCoordinate(parsed, "x")
        val y = decodeCoordinate(parsed, "y")
        return byteArrayOf(0x04) + x + y
    }

    private fun validatePublicJwkMetadata(jwk: EncodedKey.Jwk) {
        val parsed = json.parseToJsonElement(jwk.data.toByteArray().decodeToString()) as? JsonObject
            ?: throw IllegalArgumentException("HPKE recipient JWK must be an object")
        require("d" !in parsed) { "HPKE recipient JWK must not contain private material" }
        parsed["use"]?.let { value ->
            require(value is JsonPrimitive && value.isString && value.content == "enc") {
                "HPKE recipient JWK use must be enc"
            }
        }
        parsed["alg"]?.let {
            throw IllegalArgumentException("HPKE recipient JWK must not declare an unrelated JOSE algorithm")
        }
        parsed["key_ops"]?.let { value ->
            val operations = (value as? JsonArray)?.map { operation ->
                require(operation is JsonPrimitive && operation.isString) {
                    "HPKE recipient JWK key operations must be strings"
                }
                operation.content.also {
                    require(it in supportedJwkOperations) { "Unsupported HPKE recipient JWK key operation: $it" }
                }
            } ?: throw IllegalArgumentException("HPKE recipient JWK key_ops must be an array")
            require(operations.distinct().size == operations.size) {
                "HPKE recipient JWK key_ops must not contain duplicates"
            }
            require("deriveKey" in operations || "deriveBits" in operations) {
                "HPKE recipient JWK does not permit key derivation"
            }
        }
    }

    private fun decodeCoordinate(jwk: JsonObject, name: String): ByteArray {
        val value = (jwk[name] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
            ?: throw IllegalArgumentException("HPKE recipient JWK $name coordinate is missing")
        require('=' !in value) { "HPKE recipient JWK coordinates must use unpadded base64url" }
        return base64Url.decode(value).also {
            require(it.size == 32 && base64Url.encode(it) == value) {
                "HPKE recipient JWK $name coordinate is invalid"
            }
        }
    }

    private suspend fun labeledExtract(
        salt: ByteArray,
        label: String,
        ikm: ByteArray,
        suiteId: ByteArray,
        provider: CryptographyProvider,
    ): ByteArray = hkdfExtract(
        salt,
        versionLabel + suiteId + label.encodeToByteArray() + ikm,
        provider,
    )

    private suspend fun labeledExpand(
        prk: ByteArray,
        label: String,
        info: ByteArray,
        length: Int,
        suiteId: ByteArray,
        provider: CryptographyProvider,
    ): ByteArray = hkdfExpand(
        prk,
        i2osp(length) + versionLabel + suiteId + label.encodeToByteArray() + info,
        length,
        provider,
    )

    private suspend fun hkdfExtract(
        salt: ByteArray,
        ikm: ByteArray,
        provider: CryptographyProvider,
    ): ByteArray = hmacSha256(salt.takeIf { it.isNotEmpty() } ?: ByteArray(HASH_SIZE), ikm, provider)

    private suspend fun hkdfExpand(
        prk: ByteArray,
        info: ByteArray,
        length: Int,
        provider: CryptographyProvider,
    ): ByteArray {
        require(length in 0..minOf(255 * HASH_SIZE, 0xffff)) { "Invalid HKDF output length" }
        if (length == 0) return byteArrayOf()
        val output = ArrayList<Byte>(length)
        var previous = byteArrayOf()
        var counter = 1
        while (output.size < length) {
            previous = hmacSha256(prk, previous + info + counter.toByte(), provider)
            output.addAll(previous.toList())
            counter++
        }
        return output.take(length).toByteArray()
    }

    private suspend fun hmacSha256(
        key: ByteArray,
        data: ByteArray,
        provider: CryptographyProvider,
    ): ByteArray = provider.get(HMAC)
        .keyDecoder(SHA256)
        .decodeFromByteArray(HMAC.Key.Format.RAW, key)
        .signatureGenerator()
        .generateSignature(data)

    private fun i2osp(value: Int): ByteArray {
        require(value in 0..0xffff)
        return byteArrayOf((value ushr 8).toByte(), value.toByte())
    }

    private data class Context(
        val key: ByteArray,
        val baseNonce: ByteArray,
    )

    private const val MODE_BASE: Byte = 0
    private const val KEM_ID = 0x0010
    private const val KDF_ID = 0x0001
    private const val AEAD_ID = 0x0001
    private val supportedJwkOperations = setOf(
        "sign",
        "verify",
        "encrypt",
        "decrypt",
        "wrapKey",
        "unwrapKey",
        "deriveKey",
        "deriveBits",
    )
}
