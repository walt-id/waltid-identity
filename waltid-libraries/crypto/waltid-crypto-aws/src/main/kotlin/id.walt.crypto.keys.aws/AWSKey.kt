package id.walt.crypto.keys.aws

import aws.sdk.kotlin.services.kms.KmsClient
import aws.sdk.kotlin.services.kms.model.*
import id.walt.crypto.exceptions.KeyTypeNotSupportedException
import id.walt.crypto.exceptions.SigningException
import id.walt.crypto.keys.EccUtils
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.jwsSigningAlgorithm
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.kotlincrypto.hash.sha2.SHA256


@Serializable
@SerialName("aws")
class AWSKey(
    val config: AWSKeyMetadataSDK,
    val id: String,
    private var _publicKey: String? = null,
    private var _keyType: KeyType? = null,

    ) : Key() {

    @Transient
    override var keyType: KeyType
        get() = _keyType!!
        set(value) {
            _keyType = value
        }

    override val hasPrivateKey: Boolean
        get() = false


    override fun toString(): String = "[AWS ${keyType.name} key @AWS ${config.region} - $id]"

    override suspend fun getKeyId(): String = getPublicKey().getKeyId()

    override suspend fun getThumbprint(): String {
        TODO("Not yet implemented")
    }

    override suspend fun exportJWK(): String = throw NotImplementedError("JWK export is not available for remote keys.")

    override suspend fun exportJWKObject(): JsonObject = Json.parseToJsonElement(_publicKey!!).jsonObject

    override suspend fun exportPEM(): String = throw NotImplementedError("PEM export is not available for remote keys.")

    private val awsSigningAlgorithm by lazy {
        when (keyType) {
            KeyType.secp256r1, KeyType.secp256k1 -> "ECDSA_SHA_256"
            KeyType.RSA -> "RSASSA_PKCS1_V1_5_SHA_256"
            else -> throw KeyTypeNotSupportedException(keyType.name)
        }
    }

    override suspend fun signRaw(plaintext: ByteArray): ByteArray {
        if (!awsSigningAlgorithm.endsWith("_SHA_256")){
            throw SigningException("failed to sign - unsupported hashing algorithm: $awsSigningAlgorithm")
        }
        val digestedMessage = AWSKey.sha256(plaintext)

        val signRequest = SignRequest {
            this.keyId = id
            signingAlgorithm = SigningAlgorithmSpec.fromValue(awsSigningAlgorithm)
            message = digestedMessage
            messageType = MessageType.Digest // Using digest mode to handle payloads larger than 4096 bytes
        }

        return KmsClient { region = config.region }.use { kmsClient ->
            kmsClient.sign(signRequest).signature ?: throw IllegalStateException("Signature not returned")
        }
    }


    override suspend fun signJws(
        plaintext: ByteArray,
        headers: Map<String, JsonElement>
    ): String {
        val appendedHeader = HashMap(headers).apply {
            put("alg", jwsSigningAlgorithm(keyType).toJsonElement())
        }

        val header = Json.encodeToString(appendedHeader).encodeToByteArray().encodeToBase64Url()
        val payload = plaintext.encodeToBase64Url()

        var rawSignature = signRaw("$header.$payload".encodeToByteArray())

        if (keyType in listOf(KeyType.secp256r1, KeyType.secp256k1)) { // TODO: Add RSA support
            rawSignature = EccUtils.convertDERtoIEEEP1363(rawSignature)
        }

        val encodedSignature = rawSignature.encodeToBase64Url()
        val jws = "$header.$payload.$encodedSignature"

        return jws
    }

    override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?
    ): Result<ByteArray> {
        if (!awsSigningAlgorithm.endsWith("_SHA_256")){
            throw SigningException("failed to verofy - unsupported hashing algorithm: $awsSigningAlgorithm")
        }
        val messageToVerify = detachedPlaintext ?: return Result.failure(IllegalArgumentException("Detached plaintext is required for verification"))
        val digestedMessage = AWSKey.sha256(messageToVerify)

        val verifyRequest = VerifyRequest {
            this.keyId = id
            signingAlgorithm = SigningAlgorithmSpec.fromValue(awsSigningAlgorithm)
            message = digestedMessage
            this.signature = signed
            messageType = MessageType.Digest
        }

        return KmsClient { region = config.region }.use { kmsClient ->
            val response = kmsClient.verify(verifyRequest)
            Result.success(response.signatureValid.toString().decodeFromBase64())
        }
    }


    override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        val publicKey = getPublicKey()
        val verification = publicKey.verifyJws(signedJws)
        return verification
    }

    @Transient
    private var backedKey: Key? = null

    override suspend fun getPublicKey(): Key = backedKey ?: when {
        _publicKey != null -> _publicKey!!.let { JWKKey.importJWK(it).getOrThrow() }
        else -> retrievePublicKey()
    }.also { newBackedKey -> backedKey = newBackedKey }


    private suspend fun retrievePublicKey(): Key {
        val publicKey = getAwsPublicKey(config, id)
        _publicKey = publicKey.exportJWK()
        return publicKey
    }

    override suspend fun getPublicKeyRepresentation(): ByteArray {
        TODO("Not yet implemented")
    }

    override suspend fun getMeta(): KeyMeta {
        TODO("Not yet implemented")
    }

    override suspend fun deleteKey(): Boolean {
        val request = ScheduleKeyDeletionRequest {
            this.keyId = id
            pendingWindowInDays = 7
        }

        val delete = KmsClient { region = config.region }.use { kmsClient ->
            kmsClient.scheduleKeyDeletion(request)
        }
        return delete.keyState?.value == "PendingDeletion"
    }


    companion object {


        suspend fun generateKey(keyType: KeyType, config: AWSKeyMetadataSDK): AWSKey {
            val request = CreateKeyRequest {
                this.description = description
                keySpec = KeySpec.fromValue(keyTypeToAwsKeyMapping(keyType))
                keyUsage = KeyUsageType.SignVerify
            }
            val response = KmsClient { region = config.region }.use { kmsClient ->
                kmsClient.createKey(request)
            }

            val keyid = response.keyMetadata?.keyId ?: throw IllegalStateException("Key ID not returned")
            val publicKey = getAwsPublicKey(config, keyid)
            val keyType = response.keyMetadata?.keySpec?.value
            return AWSKey(
                config = config,
                id = keyid,
                _publicKey = publicKey.exportJWK(),
                _keyType = awsKeyToKeyTypeMapping(keyType.toString())
            )
        }


        suspend fun getAwsPublicKey(config: AWSKeyMetadataSDK, keyId: String): Key {
            KmsClient { region = config.region }.use { kmsClient ->
                val pk = kmsClient.getPublicKey(GetPublicKeyRequest {
                    this.keyId = keyId
                }).publicKey ?: throw IllegalStateException("Public key not returned")
                val encodedPk = pk.encodeToBase64()

                val pemKey = """
-----BEGIN PUBLIC KEY-----
$encodedPk
-----END PUBLIC KEY-----
""".trimIndent()
                val keyJWK = JWKKey.importPEM(pemKey)
                return keyJWK.getOrThrow()
            }
        }

        private fun keyTypeToAwsKeyMapping(type: KeyType) = when (type) {
            KeyType.secp256r1 -> "ECC_NIST_P256"
            KeyType.secp256k1 -> "ECC_SECG_P256K1"
            KeyType.RSA -> "RSA_2048"
            else -> throw KeyTypeNotSupportedException(type.name)
        }

        private fun awsKeyToKeyTypeMapping(type: String) = when (type) {
            "ECC_NIST_P256" -> KeyType.secp256r1
            "ECC_SECG_P256K1" -> KeyType.secp256k1
            "RSA_2048" -> KeyType.RSA
            else -> throw KeyTypeNotSupportedException(type)
        }

        // Utility to perform SHA-256 digest on binary data
        fun sha256(data: ByteArray): ByteArray = SHA256().digest(data)
    }

}
