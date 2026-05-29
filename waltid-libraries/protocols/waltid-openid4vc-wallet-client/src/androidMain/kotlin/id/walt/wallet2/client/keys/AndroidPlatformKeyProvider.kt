package id.walt.wallet2.client.keys

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import id.walt.crypto.keys.EccUtils
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JwsUtils.decodeJwsStrings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPublicKeySpec
import java.security.spec.RSAPublicKeySpec
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class AndroidPlatformKeyProvider : PlatformKeyProvider {

    override val supportedHardwareKeyTypes: Set<KeyType> =
        setOf(KeyType.secp256r1, KeyType.RSA)

    override val isHardwareBackingAvailable: Boolean = true

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun generateKey(keyType: KeyType, keyId: String?): Key {
        require(keyType in supportedHardwareKeyTypes) {
            "KeyType $keyType is not supported in Android KeyStore. Supported: $supportedHardwareKeyTypes"
        }
        val alias = keyId ?: "wallet_key_${Uuid.random()}"
        val algorithm = when (keyType) {
            KeyType.secp256r1 -> KeyProperties.KEY_ALGORITHM_EC
            KeyType.RSA -> KeyProperties.KEY_ALGORITHM_RSA
            else -> error("Unsupported key type: $keyType")
        }

        KeyPairGenerator.getInstance(algorithm, ANDROID_KEYSTORE).apply {
            initialize(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                ).apply {
                    if (keyType == KeyType.secp256r1) {
                        setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    }
                    setDigests(
                        KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA384,
                        KeyProperties.DIGEST_SHA512
                    )
                    if (keyType == KeyType.RSA) {
                        setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    }
                }.build()
            )
        }.generateKeyPair()

        return AndroidHardwareKey(alias, keyType)
    }

    override suspend fun loadKey(keyId: String, keyType: KeyType): Key? {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!ks.containsAlias(keyId)) return null
        return AndroidHardwareKey(keyId, keyType)
    }

    override suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!ks.containsAlias(keyId)) return false
        ks.deleteEntry(keyId)
        return true
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}

internal class AndroidHardwareKey(
    private val alias: String,
    override val keyType: KeyType,
) : Key() {

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override val hasPrivateKey: Boolean
        get() = keyStore.getKey(alias, null) is PrivateKey

    override suspend fun getKeyId(): String = alias

    override suspend fun getThumbprint(): String = alias

    override suspend fun exportJWK(): String = exportJWKObject().toString()

    override suspend fun exportJWKObject(): JsonObject {
        val publicKey = keyStore.getCertificate(alias)?.publicKey
            ?: error("No public key for alias: $alias")

        return when (keyType) {
            KeyType.secp256r1 -> {
                val spec = KeyFactory.getInstance("EC")
                    .getKeySpec(publicKey, ECPublicKeySpec::class.java)
                JsonObject(
                    mapOf(
                        "kty" to JsonPrimitive("EC"),
                        "crv" to JsonPrimitive("P-256"),
                        "x" to JsonPrimitive(spec.w.affineX.toByteArray().encodeToBase64Url()),
                        "y" to JsonPrimitive(spec.w.affineY.toByteArray().encodeToBase64Url()),
                    )
                )
            }
            KeyType.RSA -> {
                val spec = KeyFactory.getInstance("RSA")
                    .getKeySpec(publicKey, RSAPublicKeySpec::class.java)
                JsonObject(
                    mapOf(
                        "kty" to JsonPrimitive("RSA"),
                        "n" to JsonPrimitive(spec.modulus.toByteArray().encodeToBase64Url()),
                        "e" to JsonPrimitive(spec.publicExponent.toByteArray().encodeToBase64Url()),
                    )
                )
            }
            else -> error("Unsupported key type: $keyType")
        }
    }

    override suspend fun exportPEM(): String = error("PEM export not supported for hardware-backed keys")

    override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray {
        val privateKey = keyStore.getKey(alias, null) as PrivateKey
        return signatureInstance().run {
            initSign(privateKey)
            update(plaintext)
            sign()
        }
    }

    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
        val headerEncoded = Json.encodeToString(headers).toByteArray().encodeToBase64Url()
        val payloadEncoded = plaintext.encodeToBase64Url()
        val signingInput = "$headerEncoded.$payloadEncoded"

        val rawSignature = signRaw(signingInput.encodeToByteArray()) as ByteArray
        val jwsSignature = EccUtils.convertDERtoIEEEP1363(rawSignature).encodeToBase64Url()

        return "$signingInput.$jwsSignature"
    }

    override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?,
        customSignatureAlgorithm: String?,
    ): Result<ByteArray> {
        val plaintext = detachedPlaintext ?: return Result.failure(Exception("Detached plaintext required"))
        val cert = keyStore.getCertificate(alias)
            ?: return Result.failure(Exception("Certificate not found"))
        val valid = signatureInstance().run {
            initVerify(cert)
            update(plaintext)
            verify(signed)
        }
        return if (valid) Result.success(plaintext) else Result.failure(Exception("Signature invalid"))
    }

    override suspend fun verifyJws(signedJws: String): Result<JsonElement> = runCatching {
        val parts = signedJws.decodeJwsStrings()
        val signable = parts.getSignable()
        val signature = parts.signature
        val publicKey = keyStore.getCertificate(alias).publicKey
        val valid = signatureInstance().run {
            initVerify(publicKey)
            update(signable.toByteArray())
            verify(signature.decodeFromBase64Url())
        }
        if (!valid) error("JWS signature verification failed")
        Json.parseToJsonElement(
            parts.payload.decodeFromBase64Url().decodeToString()
        )
    }

    override suspend fun getPublicKey(): Key = this

    override suspend fun getPublicKeyRepresentation(): ByteArray =
        keyStore.getCertificate(alias).publicKey.encoded

    override suspend fun getMeta() = error("Not implemented")

    override suspend fun deleteKey(): Boolean = runCatching {
        keyStore.deleteEntry(alias)
    }.isSuccess

    private fun signatureInstance(): Signature = when (keyType) {
        KeyType.secp256r1 -> Signature.getInstance("SHA256withECDSA")
        KeyType.RSA -> Signature.getInstance("SHA256withRSA")
        else -> error("Unsupported key type: $keyType")
    }
}
