package id.walt.crypto.keys

import android.util.Base64
import id.walt.crypto.keys.AndroidKeyGenerator.PUBLIC_KEY_ALIAS_PREFIX
import id.walt.crypto.utils.JsonUtils.toJsonElement
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
import java.util.UUID

class AndroidKey() : Key() {

    override val keyType: KeyType
        get() = internalKeyType

    private lateinit var internalKeyType: KeyType

    override val hasPrivateKey: Boolean
        get() {
            return (keyStore.getKey(internalKeyId, null) as PrivateKey?) != null
        }

    private val keyStore = KeyStore.getInstance(AndroidKeyGenerator.ANDROID_KEYSTORE).apply {
        load(null)
    }

    private lateinit var internalKeyId: String

    constructor(keyAlias: KeyAlias, keyType: KeyType) : this() {
        internalKeyId = keyAlias.alias
        internalKeyType = keyType
        println("Initialised instance of AndroidKey {keyId: '$internalKeyId'}")
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
                    put("n", Base64.encodeToString(keySpec.modulus.toByteArray(), Base64.NO_WRAP))
                    put("e", Base64.encodeToString(keySpec.publicExponent.toByteArray(), Base64.NO_WRAP))
                    toString()
                }
            }

            KeyType.secp256r1 -> {
                val keyFactory = KeyFactory.getInstance("EC")
                val keySpec = keyFactory.getKeySpec(publicKey, ECPublicKeySpec::class.java)
                JSONObject().run {
                    put("kty", "EC")
                    put("crv", "P-256")
                    put("x", Base64.encodeToString(keySpec.w.affineX.toByteArray(), Base64.NO_WRAP))
                    put("y", Base64.encodeToString(keySpec.w.affineY.toByteArray(), Base64.NO_WRAP))
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

    override suspend fun signRaw(plaintext: ByteArray): ByteArray {
        check(hasPrivateKey) { "No private key is attached to this key!" }

        val privateKey: PrivateKey = keyStore.getKey(internalKeyId, null) as PrivateKey

        val signature: ByteArray? = getSignature().run {
            initSign(privateKey)
            update(plaintext)
            sign()
        }

        println("Raw message signed with signature {signature: '${Base64.encodeToString(signature, Base64.DEFAULT)}'}")
        println("Raw message signed - {raw: '${plaintext.decodeToString()}'}")

        return Base64.encodeToString(signature, Base64.DEFAULT).toByteArray()
    }

    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, String>): String {
        check(hasPrivateKey) { "No private key is attached to this key!" }

        val privateKey: PrivateKey = keyStore.getKey(internalKeyId, null) as PrivateKey

        val signature: ByteArray = getSignature().run {
            initSign(privateKey)
            update(plaintext)
            sign()
        }

        val encodedSignature = Base64.encodeToString(signature, Base64.NO_WRAP)

        // Construct the JWS in the format: base64UrlEncode(headers) + '.' + base64UrlEncode(payload) + '.' + base64UrlEncode(signature)
        val encodedHeaders = Base64.encodeToString(headers.toString().toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val encodedPayload = Base64.encodeToString(plaintext, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        return "$encodedHeaders.$encodedPayload.$encodedSignature"
    }

    override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?): Result<ByteArray> {
        val certificate: Certificate? = keyStore.getCertificate(internalKeyId)

        return if (certificate != null) {
            val signature: ByteArray = Base64.decode(signed.decodeToString(), Base64.DEFAULT)

            println("signature to verify- ${signed.decodeToString()}")
            println("plaintext - ${detachedPlaintext!!.decodeToString()}")

            val isValid: Boolean = getSignature().run {
                initVerify(certificate)
                update(detachedPlaintext)
                verify(signature)
            }

            return if (isValid) {
                Result.success(detachedPlaintext)
            } else {
                Result.failure(Exception("Signature is not valid"))
            }

        } else {
            Result.failure(Exception("Certificate not found in KeyStore"))
        }
    }

    override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        return runCatching {
            val splitJws = signedJws.split(".")
            if (splitJws.size != 3) throw IllegalArgumentException("Invalid JWS format")

            val header = String(android.util.Base64.decode(splitJws[0], android.util.Base64.URL_SAFE))
            val payload = String(android.util.Base64.decode(splitJws[1], android.util.Base64.URL_SAFE))
            val signature = android.util.Base64.decode(splitJws[2], android.util.Base64.NO_WRAP)

            // Get the public key from the Android KeyStore
            val publicKey = keyStore.getCertificate(internalKeyId).publicKey

            // Create a Signature instance and initialize it with the public key
            val sig = getSignature()
            sig.initVerify(publicKey)

            // Supply the Signature object the data to be signed
            sig.update(payload.toByteArray())

            // Verify the signature
            val isVerified = sig.verify(signature)

            if (!isVerified) throw Exception("Signature verification failed")

            // If the signature is valid, parse the payload of the JWS into a JSON Element
            payload.toJsonElement()
        }
    }

    override suspend fun getPublicKey(): AndroidKey {
        return if (hasPrivateKey) {
            val keyPair = keyStore.getEntry(internalKeyId, null) as? KeyStore.PrivateKeyEntry
            checkNotNull(keyPair) { "This AndroidKey instance does not have a KeyPair!" }

            val id = "$PUBLIC_KEY_ALIAS_PREFIX${UUID.randomUUID()}"
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

    private fun getSignature(): Signature {
        val sig = when (keyType) {
            KeyType.secp256k1 -> Signature.getInstance("SHA256withECDSA", "BC")//Legacy SunEC curve disabled
            KeyType.secp256r1 -> Signature.getInstance("SHA256withECDSA")
            KeyType.Ed25519 -> Signature.getInstance("Ed25519")
            KeyType.RSA -> Signature.getInstance("SHA256withRSA")
        }
        println("Signature instance created {algorithm: '${sig.algorithm}'}")
        return sig
    }

    companion object : AndroidKeyCreator {
        override suspend fun generate(
            type: KeyType,
            metadata: JwkKeyMeta?
        ): AndroidKey = AndroidKeyGenerator.generate(type, metadata)
    }
}