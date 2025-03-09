package id.walt.crypto.keys

import id.walt.crypto.keys.AndroidKeyGenerator.PUBLIC_KEY_ALIAS_PREFIX
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.JwsUtils.decodeJwsStrings
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.Certificate
import java.security.spec.ECPublicKeySpec
import java.security.spec.RSAPublicKeySpec
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger { }

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class AndroidKey() : Key() {

    override val keyType: KeyType
        get() = internalKeyType

    private lateinit var internalKeyType: KeyType

    override val hasPrivateKey: Boolean
        get() = keyStore.getKey(internalKeyId, null) as? PrivateKey? != null

    private val keyStore = KeyStore.getInstance(AndroidKeyGenerator.ANDROID_KEYSTORE).apply {
        load(null)
    }

    private lateinit var internalKeyId: String

    constructor(keyAlias: KeyAlias, keyType: KeyType) : this() {
        internalKeyId = keyAlias.alias
        internalKeyType = keyType
        log.trace { "Initialised instance of AndroidKey {keyId: '$internalKeyId'}" }
    }

    override suspend fun getKeyId(): String = internalKeyId

    override suspend fun getThumbprint(): String {
        TODO("Not yet implemented")
    }

    override suspend fun exportJWK(): String {
        val publicKey = keyStore.getCertificate(internalKeyId)?.publicKey
        checkNotNull(publicKey) { "This AndroidKey instance does not have a public key associated with it. This should not happen." }

        return when (internalKeyType) {
            KeyType.RSA -> {
                val keyFactory = KeyFactory.getInstance(internalKeyType.name)
                val keySpec = keyFactory.getKeySpec(publicKey, RSAPublicKeySpec::class.java)
                JSONObject().run {
                    put("kty", internalKeyType.name)
                    put("n", keySpec.modulus.toByteArray().encodeToBase64Url())
                    put("e", keySpec.publicExponent.toByteArray().encodeToBase64Url())
                    toString()
                }
            }

            KeyType.secp256r1 -> {
                val keyFactory = KeyFactory.getInstance("EC")
                val keySpec = keyFactory.getKeySpec(publicKey, ECPublicKeySpec::class.java)
                JSONObject().run {
                    put("kty", "EC")
                    put("crv", "P-256")
                    put("x", keySpec.w.affineX.toByteArray().encodeToBase64Url())
                    put("y", keySpec.w.affineY.toByteArray().encodeToBase64Url())
                    toString()
                }
            }

            KeyType.Ed25519 -> throw IllegalArgumentException("Ed25519 is not supported in Android KeyStore")
            KeyType.secp256k1 -> throw IllegalArgumentException("secp256k1 is not supported in Android KeyStore")
        }
    }

    override suspend fun exportJWKObject(): JsonObject {
        val jwkString = exportJWK()
        return Json.parseToJsonElement(jwkString) as JsonObject
    }

    override suspend fun exportPEM(): String {
        TODO("Not yet implemented")
    }

    private fun signWithKeystore(plaintext: ByteArray): ByteArray {
        check(hasPrivateKey) { "No private key is attached to this key!" }

        val privateKey: PrivateKey = keyStore.getKey(internalKeyId, null) as PrivateKey

        val signature: ByteArray = getSignature().run {
            initSign(privateKey)
            update(plaintext)
            sign()
        }

        return signature
    }

    override suspend fun signRaw(plaintext: ByteArray): ByteArray {
        val signature: ByteArray = signWithKeystore(plaintext)

        log.trace { "Raw message signed - {raw: '${plaintext.decodeToString()}'}" }

        return signature
    }

    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
        val signingInput =
            Json.encodeToString(headers).toByteArray()
                .encodeToBase64Url() + "." + plaintext.encodeToBase64Url()

        val signature = signWithKeystore(signingInput.encodeToByteArray()).let {
            EccUtils.convertDERtoIEEEP1363(it)
        }.encodeToBase64Url()

        return "$signingInput.$signature"
    }

    override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?,
    ): Result<ByteArray> {
        check(detachedPlaintext != null) { "An detached plaintext is needed." }

        val certificate: Certificate = keyStore.getCertificate(internalKeyId)
            ?: return Result.failure(Exception("Certificate not found in KeyStore"))

        log.trace { "signature to verify- ${signed.decodeToString()}" }
        log.trace { "plaintext - ${detachedPlaintext.decodeToString()}" }

        val isValid: Boolean = getSignature().run {
            initVerify(certificate)
            update(detachedPlaintext)
            verify(signed)
        }

        return when {
            isValid -> Result.success(detachedPlaintext)
            else -> Result.failure(Exception("Signature is not valid"))
        }
    }

    override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        return runCatching {
            val parts = signedJws.decodeJwsStrings()
            val (_, payload, signature) = parts
            val signable = parts.getSignable()

            // Get the public key from the Android KeyStore
            val publicKey = keyStore.getCertificate(internalKeyId).publicKey

            // Create a Signature instance and initialize it with the public key
            val androidSignature = getSignature()
            androidSignature.initVerify(publicKey)

            // Supply the Signature object the data to be signed
            androidSignature.update(signable.toByteArray())

            // Verify the signature
            val isVerified = androidSignature.verify(signature.decodeFromBase64Url())

            if (!isVerified) throw Exception("Signature verification failed")

            // If the signature is valid, parse the payload of the JWS into a JSON Element
            payload.toJsonElement()
        }
    }

    override suspend fun getPublicKey(): AndroidKey {
        return if (hasPrivateKey) {
            val keyPair = keyStore.getEntry(internalKeyId, null) as? KeyStore.PrivateKeyEntry
            checkNotNull(keyPair) { "This AndroidKey instance does not have a KeyPair!" }

            val id = "$PUBLIC_KEY_ALIAS_PREFIX${Uuid.random()}"
            keyStore.setCertificateEntry(id, keyPair.certificate)
            AndroidKey(KeyAlias(id), keyType)
        } else this
    }

    override suspend fun getPublicKeyRepresentation(): ByteArray {
        return if (hasPrivateKey) {
            val keyPair = keyStore.getEntry(internalKeyId, null) as? KeyStore.PrivateKeyEntry
            checkNotNull(keyPair) { "This AndroidKey instance does not have a KeyPair!" }
            keyPair.certificate.publicKey.encoded
        } else {
            keyStore.getCertificate(internalKeyId).publicKey.encoded
        }
    }

    override suspend fun getMeta(): KeyMeta {
        TODO("Not yet implemented")
    }

    override suspend fun deleteKey(): Boolean = kotlin.runCatching { keyStore.deleteEntry(internalKeyId) }.isSuccess

    private fun getSignature(): Signature {
        val sig = when (keyType) {
            KeyType.secp256k1 -> Signature.getInstance(
                "SHA256withECDSA",
                "BC"
            )//Legacy SunEC curve disabled
            KeyType.secp256r1 -> Signature.getInstance("SHA256withECDSA")
            KeyType.Ed25519 -> Signature.getInstance("Ed25519")
            KeyType.RSA -> Signature.getInstance("SHA256withRSA")
        }
        log.trace { "Signature instance created {algorithm: '${sig.algorithm}'}" }
        return sig
    }

    companion object : AndroidKeyCreator, AndroidKeyLoader {
        override suspend fun generate(
            type: KeyType,
            metadata: AndroidKeyParameters?,
        ): AndroidKey = AndroidKeyGenerator.generate(type, metadata)

        override suspend fun load(type: KeyType, keyId: String): AndroidKey? =
            AndroidKeystoreLoader.load(type, keyId)
    }
}
