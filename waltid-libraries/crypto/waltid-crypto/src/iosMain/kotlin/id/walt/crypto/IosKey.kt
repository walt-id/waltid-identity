package id.walt.crypto

import at.asitplus.signum.indispensable.CryptoSignature
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.SignatureAlgorithm
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.JwsAlgorithm
import at.asitplus.signum.indispensable.josef.JwsCompact
import at.asitplus.signum.indispensable.josef.JwsHeader
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.josef.toJsonWebKey
import at.asitplus.signum.supreme.SignatureResult
import at.asitplus.signum.supreme.os.IosKeychainProvider
import at.asitplus.signum.supreme.sign.SignatureInput
import at.asitplus.signum.supreme.sign.verifierFor
import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Suppress("unused")
class IosKey private constructor(
    private val options: Options,
    override val hasPrivateKey: Boolean = false
) : Key() {

    class Options @OptIn(ExperimentalUuidApi::class) constructor(
        val kid: String = Uuid.random().toString(),
        val keyType: KeyType,
        val inSecureElement: Boolean = false
    ) {
        init {
            if (inSecureElement) {
                require(keyType == KeyType.secp256r1) { "kid: $kid, Error: Only KeyType.secp256r1 can be stored in secure element." }
            }
        }
    }

    companion object {
        suspend fun create(options: Options): Key {
            val curve = when (options.keyType) {
                KeyType.secp256r1 -> ECCurve.SECP_256_R_1
                KeyType.Ed25519 -> error("Ed25519 is not yet supported on iOS")
                KeyType.RSA -> null
                else -> error("Unsupported key type: ${options.keyType}")
            }


            val result = if (curve != null) {
                IosKeychainProvider.createSigningKey(options.kid) {
                    ec {
                        this.curve = curve
                    }
                    if (options.inSecureElement) {
                        hardware { backing = at.asitplus.signum.supreme.dsl.REQUIRED }
                    }
                }
            } else {
                IosKeychainProvider.createSigningKey(options.kid) {
                    rsa { }
                }
            }


            result.getOrThrow()

            return IosKey(options, true)
        }

        suspend fun load(options: Options): Key {
            IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()
            return IosKey(options, true)
        }

        suspend fun delete(kid: String, type: KeyType) {
            IosKeychainProvider.deleteSigningKey(kid).getOrThrow()
        }
    }

    override val keyType get() = options.keyType

    override suspend fun getKeyId(): String = options.kid

    override suspend fun getThumbprint(): String {
        val signer = IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()
        val jwk = signer.publicKey.toJsonWebKey(options.kid)
        return jwk.jwkThumbprint
    }

    override suspend fun exportJWK(): String = exportJWKObject().toString()

    override suspend fun exportJWKObject(): JsonObject {
        val signer = IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()
        val jwkStr = joseCompliantSerializer.encodeToString(signer.publicKey.toJsonWebKey(options.kid))
        return kotlinx.serialization.json.Json.parseToJsonElement(jwkStr).let { it as JsonObject }
    }

    override suspend fun exportPEM(): String {
        val signer = IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()
        val derBytes = signer.publicKey.encodeToTlv().derEncoded
        val base64 = kotlin.io.encoding.Base64.Mime.encode(derBytes)
        return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----"
    }

    override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): Any {
        check(hasPrivateKey) { "Only private key can do signing." }
        val signer = IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()
        val result = signer.sign(plaintext)
        check(result is SignatureResult.Success) { "Signing failed: $result" }
        return result.signature.rawByteArray
    }

    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
        check(hasPrivateKey) { "Only private key can do signing." }
        val signer = IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()

        val jwsAlgorithm = when (options.keyType) {
            KeyType.secp256r1 -> JwsAlgorithm.Signature.EC.ES256
            KeyType.RSA -> JwsAlgorithm.Signature.RSA.RS256
            else -> error("Unsupported key type for JWS: ${options.keyType}")
        }

        val jwkHeader = headers["jwk"]?.let { jwkElement ->
            runCatching { joseCompliantSerializer.decodeFromString<JsonWebKey>(jwkElement.toString()) }.getOrNull()
        }

        val header = JwsHeader(
            algorithm = jwsAlgorithm,
            keyId = headers["kid"]?.let { (it as? JsonPrimitive)?.content },
            type = headers["typ"]?.let { (it as? JsonPrimitive)?.content },
            contentType = headers["cty"]?.let { (it as? JsonPrimitive)?.content },
            jsonWebKey = jwkHeader,
        )

        val jws = JwsCompact(
            protectedHeader = header,
            payload = plaintext,
            signer = { data ->
                val signResult = signer.sign(data)
                check(signResult is SignatureResult.Success) { "JWS signing failed: $signResult" }
                signResult.signature.rawByteArray
            }
        )
        return jws.toString()
    }

    override suspend fun verifyRaw(
        signed: ByteArray, detachedPlaintext: ByteArray?,
        customSignatureAlgorithm: String?
    ): Result<ByteArray> = runCatching {
        val signer = IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()
        val cryptoPubKey = signer.publicKey

        val sigAlg = when (options.keyType) {
            KeyType.secp256r1 -> SignatureAlgorithm.ECDSAwithSHA256
            KeyType.RSA -> SignatureAlgorithm.RSAwithSHA256andPKCS1Padding
            else -> error("Unsupported key type for verification: ${options.keyType}")
        }

        val verifier = sigAlg.verifierFor(cryptoPubKey).getOrThrow()
        val signature = CryptoSignature.EC.fromRawBytes(signed)
        val plaintext = requireNotNull(detachedPlaintext) { "Detached plaintext required" }
        verifier.verify(SignatureInput(plaintext), signature).getOrThrow()
        plaintext
    }

    override suspend fun verifyJws(signedJws: String): Result<JsonElement> = runCatching {
        val parsed = JwsCompact(signedJws)
        val signer = IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()
        val cryptoPubKey = signer.publicKey

        val sigAlg = when (options.keyType) {
            KeyType.secp256r1 -> SignatureAlgorithm.ECDSAwithSHA256
            KeyType.RSA -> SignatureAlgorithm.RSAwithSHA256andPKCS1Padding
            else -> error("Unsupported key type for verification: ${options.keyType}")
        }

        val verifier = sigAlg.verifierFor(cryptoPubKey).getOrThrow()
        val signature = CryptoSignature.EC.fromRawBytes(parsed.plainSignature)
        verifier.verify(SignatureInput(parsed.signatureInput), signature).getOrThrow()

        kotlinx.serialization.json.Json.parseToJsonElement(parsed.plainPayload.decodeToString())
    }

    override suspend fun getPublicKey(): Key = IosKey(options, false)

    override suspend fun getPublicKeyRepresentation(): ByteArray {
        val signer = IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()
        return signer.publicKey.encodeToTlv().derEncoded
    }

    override suspend fun getMeta(): KeyMeta = JwkKeyMeta(getKeyId())

    override suspend fun deleteKey(): Boolean = runCatching {
        IosKeychainProvider.deleteSigningKey(options.kid).getOrThrow()
    }.isSuccess
}
