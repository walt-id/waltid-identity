package id.walt.crypto.keys.aws

import aws.sdk.kotlin.services.kms.KmsClient
import aws.sdk.kotlin.services.kms.model.*
import id.walt.crypto.exceptions.KeyTypeNotSupportedException
import id.walt.crypto.exceptions.SigningException
import id.walt.crypto.keys.*
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
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

    private val log = KotlinLogging.logger { }

    @Transient
    override var keyType: KeyType
        get() = _keyType ?: throw IllegalStateException("Key type not initialized. Call init() first or provide _keyType.")
        set(value) {
            _keyType = value
        }

    override val hasPrivateKey: Boolean
        get() = true

    override suspend fun init() {
        if (_keyType == null) {
            val publicKey = getPublicKey()
            _keyType = publicKey.keyType
        }
    }

    override fun toString(): String = "[AWS ${keyType.name} key @AWS ${config.auth.region} - $id]"

    override suspend fun getKeyId(): String = getPublicKey().getKeyId()

    override suspend fun getThumbprint(): String {
        TODO("Not yet implemented")
    }

    override suspend fun exportJWK(): String = throw NotImplementedError("JWK export is not available for remote keys.")

    override suspend fun exportJWKObject(): JsonObject = Json.parseToJsonElement(_publicKey!!).jsonObject

    override suspend fun exportPEM(): String = throw NotImplementedError("PEM export is not available for remote keys.")

    // See: https://docs.aws.amazon.com/kms/latest/APIReference/API_Sign.html
    private val awsSigningAlgorithm by lazy {
        when (keyType) {
            KeyType.secp256r1, KeyType.secp256k1 -> "ECDSA_SHA_256"
            KeyType.secp384r1 -> "ECDSA_SHA_384"
            KeyType.secp521r1 -> "ECDSA_SHA_512"
            KeyType.RSA -> "RSASSA_PKCS1_V1_5_SHA_256"
            KeyType.RSA3072 -> "RSASSA_PKCS1_V1_5_SHA_384"
            KeyType.RSA4096 -> "RSASSA_PKCS1_V1_5_SHA_512"
            KeyType.Ed25519 -> throw KeyTypeNotSupportedException(keyType.name)
        }
    }

    // =========================================================================
    // Failover Support (Phase 3)
    // =========================================================================

    /**
     * Lazily initialized failover client for multi-region keys.
     * Only created when failover is enabled and replica regions are configured.
     */
    @Transient
    private val failoverClient: FailoverKmsClient? by lazy {
        if (config.enableFailover == true && !config.replicaRegions.isNullOrEmpty()) {
            FailoverKmsClient(
                primaryRegion = config.auth.region,
                failoverRegions = config.failoverOrder ?: config.replicaRegions,
                enableFailover = true
            )
        } else null
    }

    /**
     * Execute a KMS operation with automatic failover to replica regions if enabled.
     * Falls back to single-region execution if failover is not configured.
     */
    private suspend fun <T> executeWithFailover(operation: suspend (KmsClient) -> T): T {
        return if (failoverClient != null) {
            failoverClient!!.execute(operation)
        } else {
            KmsClient { region = config.auth.region }.use { kms ->
                operation(kms)
            }
        }
    }

    // =========================================================================
    // Signing & Verification
    // =========================================================================

    override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray {
        if (!awsSigningAlgorithm.endsWith("_SHA_256")) {
            throw SigningException("failed to sign - unsupported hashing algorithm: $awsSigningAlgorithm")
        }
        val digestedMessage = sha256(plaintext)

        val signRequest = SignRequest {
            this.keyId = id
            signingAlgorithm = SigningAlgorithmSpec.fromValue(awsSigningAlgorithm)
            message = digestedMessage
            messageType = MessageType.Digest // Using digest mode to handle payloads larger than 4096 bytes
        }

        return executeWithFailover { kmsClient ->
            kmsClient.sign(signRequest).signature ?: throw IllegalStateException("Signature not returned")
        }
    }

    override suspend fun signJws(
        plaintext: ByteArray,
        headers: Map<String, JsonElement>,
    ): String {
        val appendedHeader = HashMap(headers).apply {
            put("alg", keyType.jwsAlg.toJsonElement())
        }

        val header = Json.encodeToString(appendedHeader).encodeToByteArray().encodeToBase64Url()
        val payload = plaintext.encodeToBase64Url()

        var rawSignature = signRaw("$header.$payload".encodeToByteArray())

        if (keyType in KeyTypes.EC_KEYS) { // TODO: Add RSA support
            rawSignature = EccUtils.convertDERtoIEEEP1363(rawSignature)
        }

        val encodedSignature = rawSignature.encodeToBase64Url()
        val jws = "$header.$payload.$encodedSignature"

        return jws
    }

    override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?, customSignatureAlgorithm: String?): Result<ByteArray> {
        if (!awsSigningAlgorithm.endsWith("_SHA_256")) {
            throw SigningException("failed to verify - unsupported hashing algorithm: $awsSigningAlgorithm")
        }
        val messageToVerify =
            detachedPlaintext ?: return Result.failure(IllegalArgumentException("Detached plaintext is required for verification"))
        val digestedMessage = sha256(messageToVerify)

        val verifyRequest = VerifyRequest {
            this.keyId = id
            signingAlgorithm = SigningAlgorithmSpec.fromValue(awsSigningAlgorithm)
            message = digestedMessage
            this.signature = signed
            messageType = MessageType.Digest
        }

        return executeWithFailover { kmsClient ->
            val response = kmsClient.verify(verifyRequest)
            Result.success(response.signatureValid.toString().decodeFromBase64())
        }
    }

    override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        val publicKey = getPublicKey()
        val verification = publicKey.verifyJws(signedJws)
        return verification
    }

    // =========================================================================
    // Public Key Retrieval
    // =========================================================================

    @Transient
    private var backedKey: Key? = null

    override suspend fun getPublicKey(): Key = backedKey ?: when {
        _publicKey != null -> _publicKey!!.let { JWKKey.importJWK(it).getOrThrow() }
        else -> retrievePublicKey()
    }.also { newBackedKey -> backedKey = newBackedKey }

    private suspend fun retrievePublicKey(): Key {
        val publicKey = executeWithFailover { kmsClient ->
            val pk = kmsClient.getPublicKey(GetPublicKeyRequest {
                this.keyId = id
            }).publicKey ?: throw IllegalStateException("Public key not returned")
            val encodedPk = pk.encodeToBase64()

            val pemKey = """
-----BEGIN PUBLIC KEY-----
$encodedPk
-----END PUBLIC KEY-----
""".trimIndent()
            JWKKey.importPEM(pemKey).getOrThrow()
        }
        _publicKey = publicKey.exportJWK()
        return publicKey
    }

    override suspend fun getPublicKeyRepresentation(): ByteArray {
        TODO("Not yet implemented")
    }

    override suspend fun getMeta(): KeyMeta {
        TODO("Not yet implemented")
    }

    // =========================================================================
    // Key Deletion (Phase 2: Cascade Delete)
    // =========================================================================

    /**
     * Delete this key. For multi-region keys, this performs a cascade delete:
     * 1. Schedule deletion of all replica keys first
     * 2. Wait for replicas to be in PendingDeletion state
     * 3. Schedule deletion of the primary key
     *
     * AWS requires all replicas to be deleted before the primary can be deleted.
     */
    override suspend fun deleteKey(): Boolean {
        val isMultiRegion = checkIsMultiRegionKey()

        return if (isMultiRegion) {
            deleteMultiRegionKey()
        } else {
            deleteSingleRegionKey()
        }
    }

    /**
     * Check if this key is a multi-region key by querying AWS KMS metadata.
     */
    private suspend fun checkIsMultiRegionKey(): Boolean {
        return KmsClient { region = config.auth.region }.use { kms ->
            val metadata = kms.describeKey(DescribeKeyRequest { keyId = id })
            metadata.keyMetadata?.multiRegion == true
        }
    }

    /**
     * Get the list of regions where replica keys exist for this multi-region key.
     */
    private suspend fun getReplicaRegions(): List<String> {
        return KmsClient { region = config.auth.region }.use { kms ->
            val metadata = kms.describeKey(DescribeKeyRequest { keyId = id })
            metadata.keyMetadata?.multiRegionConfiguration?.replicaKeys
                ?.mapNotNull { it.region }
                ?: emptyList()
        }
    }

    /**
     * Delete a multi-region key by first deleting all replicas, then the primary.
     * Includes retry logic to handle the case where replicas haven't yet reached
     * PendingDeletion state when we try to delete the primary.
     */
    private suspend fun deleteMultiRegionKey(): Boolean {
        val replicaRegions = getReplicaRegions()

        // Step 1: Schedule deletion of each replica
        for (replicaRegion in replicaRegions) {
            try {
                KmsClient { region = replicaRegion }.use { kms ->
                    kms.scheduleKeyDeletion(ScheduleKeyDeletionRequest {
                        keyId = id
                        pendingWindowInDays = 7
                    })
                }
            } catch (e: Exception) {
                // Log but continue - replica may already be deleted or in pending state
                log.warn { "Failed to schedule deletion of replica in $replicaRegion: ${e.message}" }
            }
        }

        // Step 2: Retry primary deletion with backoff
        // AWS may reject if replicas haven't reached PendingDeletion yet
        var lastException: Exception? = null
        repeat(5) { attempt ->
            try {
                return deleteSingleRegionKey()
            } catch (e: Exception) {
                lastException = e
                val message = e.message ?: ""
                // Check if this is a "replica not yet deleted" error
                if (message.contains("replica", ignoreCase = true) ||
                    message.contains("multi-region", ignoreCase = true)) {
                    if (attempt < 4) {
                        delay(2000L * (attempt + 1)) // 2s, 4s, 6s, 8s backoff
                        return@repeat
                    }
                }
                throw e
            }
        }
        throw lastException ?: IllegalStateException("Failed to delete multi-region key after retries")
    }

    /**
     * Delete a single-region key (or the primary of a multi-region key after replicas are deleted).
     */
    private suspend fun deleteSingleRegionKey(): Boolean {
        val request = ScheduleKeyDeletionRequest {
            this.keyId = id
            pendingWindowInDays = 7
        }

        val delete = KmsClient { region = config.auth.region }.use { kmsClient ->
            kmsClient.scheduleKeyDeletion(request)
        }
        return delete.keyState?.value == "PendingDeletion"
    }

    // =========================================================================
    // Companion Object: Key Generation & Utilities
    // =========================================================================

    companion object {

        /**
         * Generate a new AWS KMS key. For multi-region keys with replicaRegions specified,
         * this will also create replica keys in the specified regions.
         *
         * @param keyType The cryptographic key type (e.g., secp256r1, RSA)
         * @param config Configuration including region, multi-region settings, and replica regions
         * @return The generated AWSKey instance
         */
        suspend fun generateKey(keyType: KeyType, config: AWSKeyMetadataSDK): AWSKey {

            val awsTags = config.tags
                ?.map { (key, value) ->
                    Tag {
                        tagKey = key
                        tagValue = value
                    }
                }
                ?.takeIf { it.isNotEmpty() }

            val request = CreateKeyRequest {
                description = config.keyName
                keySpec = KeySpec.fromValue(keyTypeToAwsKeyMapping(keyType))
                keyUsage = KeyUsageType.SignVerify
                tags = awsTags
                multiRegion = config.multiRegion
            }

            val response = KmsClient { region = config.auth.region }.use { kms ->
                kms.createKey(request)
            }

            val keyId = response.keyMetadata?.keyId
                ?: throw IllegalStateException("Key ID not returned by AWS KMS")

            // Create alias if keyName is specified
            config.keyName?.let { keyName ->
                KmsClient { region = config.auth.region }.use { kms ->
                    kms.createAlias(
                        CreateAliasRequest {
                            aliasName = "alias/$keyName"
                            targetKeyId = keyId
                        }
                    )
                }
            }

            // Phase 1: Create replicas if multi-region with specified regions
            if (config.multiRegion == true && !config.replicaRegions.isNullOrEmpty()) {
                createReplicas(keyId, config.auth.region, config.replicaRegions)
            }

            val publicKey = getAwsPublicKey(config, keyId)
            val resolvedKeyType = response.keyMetadata?.keySpec?.value

            // Remove tags from config (they're stored in AWS, not needed in our metadata)
            val editedConfig = config.copy(tags = null)

            return AWSKey(
                config = editedConfig,
                id = keyId,
                _publicKey = publicKey.exportJWK(),
                _keyType = awsKeyToKeyTypeMapping(resolvedKeyType.toString())
            )
        }

        /**
         * Create replica keys in the specified regions for a multi-region primary key.
         * Replicas share the same key material and key ID as the primary.
         *
         * Note: AWS KMS multi-region keys have eventual consistency. The primary key
         * may not be immediately visible in other regions for replication. This method
         * includes retry logic to handle this propagation delay.
         *
         * @param primaryKeyId The key ID of the primary multi-region key
         * @param primaryRegion The region of the primary key (skipped in replica creation)
         * @param replicaRegions List of regions where replicas should be created
         */
        private suspend fun createReplicas(
            primaryKeyId: String,
            primaryRegion: String,
            replicaRegions: List<String>
        ) {
            val log = KotlinLogging.logger {}
            
            for (targetRegion in replicaRegions) {
                if (targetRegion == primaryRegion) continue // Skip primary region

                // Retry with exponential backoff for eventual consistency
                var lastException: Exception? = null
                repeat(5) { attempt ->
                    try {
                        KmsClient { region = targetRegion }.use { kms ->
                            kms.replicateKey(ReplicateKeyRequest {
                                keyId = primaryKeyId
                                replicaRegion = targetRegion
                            })
                        }
                        log.info { "Created replica key in $targetRegion for primary $primaryKeyId" }
                        return@repeat // Success, exit retry loop
                    } catch (e: Exception) {
                        lastException = e
                        val message = e.message ?: ""
                        // Check if this is a "key not found" error due to eventual consistency
                        if (message.contains("does not exist", ignoreCase = true) ||
                            message.contains("not found", ignoreCase = true)) {
                            if (attempt < 4) {
                                val delayMs = 1000L * (attempt + 1) // 1s, 2s, 3s, 4s
                                log.debug { "Key not yet visible in $targetRegion, retrying in ${delayMs}ms (attempt ${attempt + 1}/5)" }
                                delay(delayMs)
                                return@repeat // Continue to next retry
                            }
                        }
                        // Non-retryable error or max retries reached
                        throw IllegalStateException(
                            "Failed to create replica in region $targetRegion for key $primaryKeyId: ${e.message}",
                            e
                        )
                    }
                }
                // If we exit the repeat loop without success and lastException is set, throw it
                lastException?.let {
                    throw IllegalStateException(
                        "Failed to create replica in region $targetRegion for key $primaryKeyId after retries: ${it.message}",
                        it
                    )
                }
            }
        }

        /**
         * Retrieve the public key from AWS KMS for a given key ID.
         */
        suspend fun getAwsPublicKey(config: AWSKeyMetadataSDK, keyId: String): Key {
            KmsClient { region = config.auth.region }.use { kmsClient ->
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

        // See: https://docs.aws.amazon.com/kms/latest/developerguide/symm-asymm-choose-key-spec.html
        private fun keyTypeToAwsKeyMapping(type: KeyType) = when (type) {
            KeyType.secp256r1 -> "ECC_NIST_P256"
            KeyType.secp384r1 -> "ECC_NIST_P384"
            KeyType.secp521r1 -> "ECC_NIST_P521"
            KeyType.secp256k1 -> "ECC_SECG_P256K1"
            KeyType.RSA -> "RSA_2048"
            KeyType.RSA3072 -> "RSA_3072"
            KeyType.RSA4096 -> "RSA_4096"
            KeyType.Ed25519 -> throw KeyTypeNotSupportedException(type.name)
        }

        private fun awsKeyToKeyTypeMapping(type: String) = when (type) {
            "ECC_NIST_P256" -> KeyType.secp256r1
            "ECC_NIST_P384" -> KeyType.secp384r1
            "ECC_NIST_P521" -> KeyType.secp521r1
            "ECC_SECG_P256K1" -> KeyType.secp256k1
            "RSA_2048" -> KeyType.RSA
            "RSA_3072" -> KeyType.RSA3072
            "RSA_4096" -> KeyType.RSA4096
            else -> throw KeyTypeNotSupportedException(type)
        }

        // Utility to perform SHA-256 digest on binary data
        fun sha256(data: ByteArray): ByteArray = SHA256().digest(data)
    }
}
