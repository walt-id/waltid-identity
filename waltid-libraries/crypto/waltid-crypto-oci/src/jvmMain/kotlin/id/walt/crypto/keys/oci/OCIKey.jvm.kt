package id.walt.crypto.keys.oci

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider
import com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider
import com.oracle.bmc.keymanagement.KmsCryptoClient
import com.oracle.bmc.keymanagement.KmsManagementClient
import com.oracle.bmc.keymanagement.KmsVaultClient
import com.oracle.bmc.keymanagement.model.*
import com.oracle.bmc.keymanagement.requests.*
import id.walt.crypto.keys.*
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*
import org.kotlincrypto.hash.sha2.SHA256
import java.time.Duration
import java.util.Date
import kotlin.io.encoding.Base64
import kotlin.time.measureTime
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration


private val log = KotlinLogging.logger { }

private fun OCIsdkMetadata.createAuthProvider(): AbstractAuthenticationDetailsProvider = when (authType) {
    "CONFIG_FILE" -> ConfigFileAuthenticationDetailsProvider(configFilePath, configProfile)
    "RESOURCE_PRINCIPAL" -> ResourcePrincipalAuthenticationDetailsProvider.builder().build()
    else -> InstancePrincipalsAuthenticationDetailsProvider.builder().build()
}

@Serializable
@SerialName("oci")
actual class OCIKey actual constructor(
    actual val id: String,
    actual val config: OCIsdkMetadata,

    @Suppress("CanBeParameter", "RedundantSuppression") private var _publicKey: String?,
    private var _keyType: KeyType?,
) : Key() {

    actual override var keyType: KeyType
        get() = _keyType!!
        set(value) {
            _keyType = value
        }

    actual override val hasPrivateKey: Boolean
        get() = false

    private suspend fun retrievePublicKey(): Key {
        val getKeyRequest = GetKeyRequest.builder().keyId(id).build()
        val response = kmsManagementClient.getKey(getKeyRequest)
        return getOCIPublicKey(kmsManagementClient, response.key.currentKeyVersion, id)
    }

    // OCI clients are initialised lazily on first use so that an OCIKey constructed with a
    // pre-loaded _publicKey can be introspected without requiring live OCI connectivity.
    @Suppress("TRANSIENT_IS_REDUNDANT")
    @Transient private var _mgmt: KmsManagementClient? = null

    @Suppress("TRANSIENT_IS_REDUNDANT")
    @Transient private var _crypto: KmsCryptoClient? = null

    private fun ensureConnected() {
        if (_mgmt != null) return
        val provider = config.createAuthProvider()
        val vaultClient = KmsVaultClient.builder().build(provider)
        val vault = getVault(vaultClient, config.vaultId)
        _mgmt = KmsManagementClient.builder().endpoint(vault.managementEndpoint).build(provider)
        _crypto = KmsCryptoClient.builder().endpoint(vault.cryptoEndpoint).build(provider)
    }

    private val kmsManagementClient: KmsManagementClient
        get() { ensureConnected(); return _mgmt!! }

    private val kmsCryptoClient: KmsCryptoClient
        get() { ensureConnected(); return _crypto!! }


    actual override fun toString(): String = "[OCI ${keyType.name} key @ ${config.vaultId}]"

    actual override suspend fun getKeyId(): String = getPublicKey().getKeyId()

    actual override suspend fun getThumbprint(): String = getPublicKey().getThumbprint()

    actual override suspend fun exportJWK(): String =
        throw NotImplementedError("JWK export is not available for remote keys.")

    actual override suspend fun exportJWKObject(): JsonObject = Json.parseToJsonElement(_publicKey!!).jsonObject

    actual override suspend fun exportPEM(): String =
        throw NotImplementedError("PEM export is not available for remote keys.")

    private fun keyTypeToSigningAlgorithm(): SignDataDetails.SigningAlgorithm = when (keyType) {
        KeyType.secp256r1 -> SignDataDetails.SigningAlgorithm.EcdsaSha256
        KeyType.secp384r1 -> SignDataDetails.SigningAlgorithm.EcdsaSha384
        KeyType.secp521r1 -> SignDataDetails.SigningAlgorithm.EcdsaSha512
        KeyType.RSA -> SignDataDetails.SigningAlgorithm.Sha256RsaPkcsPss
        KeyType.RSA3072 -> SignDataDetails.SigningAlgorithm.Sha384RsaPkcsPss
        KeyType.RSA4096 -> SignDataDetails.SigningAlgorithm.Sha512RsaPkcsPss
        else -> throw IllegalArgumentException("Key type not supported by OCI KMS: $keyType")
    }

    private fun keyTypeToVerifyingAlgorithm(): VerifyDataDetails.SigningAlgorithm = when (keyType) {
        KeyType.secp256r1 -> VerifyDataDetails.SigningAlgorithm.EcdsaSha256
        KeyType.secp384r1 -> VerifyDataDetails.SigningAlgorithm.EcdsaSha384
        KeyType.secp521r1 -> VerifyDataDetails.SigningAlgorithm.EcdsaSha512
        KeyType.RSA -> VerifyDataDetails.SigningAlgorithm.Sha256RsaPkcsPss
        KeyType.RSA3072 -> VerifyDataDetails.SigningAlgorithm.Sha384RsaPkcsPss
        KeyType.RSA4096 -> VerifyDataDetails.SigningAlgorithm.Sha512RsaPkcsPss
        else -> throw IllegalArgumentException("Key type not supported by OCI KMS: $keyType")
    }

    actual override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray {
        // OCI KMS requires a Base64-encoded digest with messageType=DIGEST
        val encodedDigest = Base64.encode(SHA256().digest(plaintext))

        val signDataDetails = SignDataDetails.builder()
            .keyId(id)
            .message(encodedDigest)
            .messageType(SignDataDetails.MessageType.Digest)
            .signingAlgorithm(keyTypeToSigningAlgorithm())
            .keyVersionId(getKeyVersion(kmsManagementClient, id))
            .build()

        val response = kmsCryptoClient.sign(SignRequest.builder().signDataDetails(signDataDetails).build())
        return response.signedData.signature.decodeFromBase64()
    }

    private val _internalJwsAlgorithm by lazy { JWSAlgorithm.parse(keyType.jwsAlg) }

    actual override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
        val jwsObject = JWSObject(
            JWSHeader.Builder(_internalJwsAlgorithm).customParams(headers).build(),
            Payload(plaintext)
        )

        val payloadToSign =
            jwsObject.header.toBase64URL().toString() + '.' + jwsObject.payload.toBase64URL().toString()
        var signed = signRaw(payloadToSign.encodeToByteArray())

        if (keyType in KeyTypes.EC_KEYS) {
            // OCI returns DER; JWS requires IEEE P1363 (raw R||S) for EC
            signed = EccUtils.convertDERtoIEEEP1363(signed)
        }

        return "$payloadToSign.${signed.encodeToBase64Url()}"
    }

    actual override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?,
        customSignatureAlgorithm: String?
    ): Result<ByteArray> {
        requireNotNull(detachedPlaintext) { "detachedPlaintext is required for OCI raw verification" }

        val encodedDigest = Base64.encode(SHA256().digest(detachedPlaintext))
        val encodedSignature = Base64.encode(signed)

        val verifyDataDetails = VerifyDataDetails.builder()
            .keyId(id)
            .message(encodedDigest)
            .messageType(VerifyDataDetails.MessageType.Digest)
            .signature(encodedSignature)
            .signingAlgorithm(keyTypeToVerifyingAlgorithm())
            .build()

        val response = kmsCryptoClient.verify(VerifyRequest.builder().verifyDataDetails(verifyDataDetails).build())

        return if (response.verifiedData.isSignatureValid) {
            Result.success(detachedPlaintext)
        } else {
            Result.failure(Exception("OCI signature verification failed"))
        }
    }

    actual override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        val parts = signedJws.split(".")
        check(parts.size == 3) { "Invalid JWT part count: ${parts.size} instead of 3" }

        val header = parts[0]
        val payload = parts[1]

        val headers: Map<String, JsonElement> =
            Json.decodeFromString(header.decodeFromBase64Url().decodeToString())
        headers["alg"]?.let {
            val algValue = it.jsonPrimitive.content
            check(algValue == keyType.jwsAlg) {
                "Invalid key algorithm for JWS: JWS has $algValue, key is ${keyType.jwsAlg}!"
            }
        }

        val signable = "$header.$payload".encodeToByteArray()
        // JWS uses IEEE P1363 for EC; OCI verify expects DER
        val rawSignature = parts[2].decodeFromBase64Url()
        val derSignature = if (keyType in KeyTypes.EC_KEYS) {
            EccUtils.convertP1363toDER(rawSignature)
        } else {
            rawSignature
        }

        return verifyRaw(derSignature, signable).map {
            Json.parseToJsonElement(payload.decodeFromBase64Url().decodeToString())
        }
    }

    @Transient
    private var backedKey: Key? = null

    actual override suspend fun getPublicKey(): Key = backedKey ?: when {
        _publicKey != null -> JWKKey.importJWK(_publicKey!!).getOrThrow()
        else -> retrievePublicKey()
    }.also { backedKey = it }

    actual override suspend fun getPublicKeyRepresentation(): ByteArray =
        getPublicKey().getPublicKeyRepresentation()

    actual override suspend fun getMeta(): OciKeyMeta = OciKeyMeta(
        keyId = id,
        keyVersion = getKeyVersion(kmsManagementClient, id),
    )

    // OCI requires a minimum 7-day pending window before deletion takes effect
    override suspend fun deleteKey(): Boolean = runCatching {
        val sevenDaysFromNow = Date(System.currentTimeMillis() + 7L * 24 * 3600 * 1000)
        val scheduleKeyDeletionDetails = ScheduleKeyDeletionDetails.builder()
            .timeOfDeletion(sevenDaysFromNow)
            .build()
        val request = ScheduleKeyDeletionRequest.builder()
            .keyId(id)
            .scheduleKeyDeletionDetails(scheduleKeyDeletionDetails)
            .build()
        kmsManagementClient.scheduleKeyDeletion(request)
    }.isSuccess


    actual companion object {

        actual val DEFAULT_KEY_LENGTH: Int = 32

        private fun keyTypeToOciShape(type: KeyType): KeyShape = when (type) {
            KeyType.secp256r1 -> KeyShape.builder()
                .algorithm(KeyShape.Algorithm.Ecdsa).length(32).curveId(KeyShape.CurveId.NistP256).build()
            KeyType.secp384r1 -> KeyShape.builder()
                .algorithm(KeyShape.Algorithm.Ecdsa).length(48).curveId(KeyShape.CurveId.NistP384).build()
            KeyType.secp521r1 -> KeyShape.builder()
                .algorithm(KeyShape.Algorithm.Ecdsa).length(66).curveId(KeyShape.CurveId.NistP521).build()
            KeyType.RSA -> KeyShape.builder().algorithm(KeyShape.Algorithm.Rsa).length(256).build()
            KeyType.RSA3072 -> KeyShape.builder().algorithm(KeyShape.Algorithm.Rsa).length(384).build()
            KeyType.RSA4096 -> KeyShape.builder().algorithm(KeyShape.Algorithm.Rsa).length(512).build()
            KeyType.secp256k1 -> throw IllegalArgumentException("secp256k1 is not supported by OCI KMS")
            KeyType.Ed25519 -> throw IllegalArgumentException("Ed25519 is not supported by OCI KMS")
        }

        private fun ociShapeToKeyType(shape: KeyShape): KeyType = when {
            shape.algorithm == KeyShape.Algorithm.Ecdsa -> when (shape.curveId) {
                KeyShape.CurveId.NistP384 -> KeyType.secp384r1
                KeyShape.CurveId.NistP521 -> KeyType.secp521r1
                else -> KeyType.secp256r1
            }
            shape.algorithm == KeyShape.Algorithm.Rsa -> when (shape.length) {
                384 -> KeyType.RSA3072
                512 -> KeyType.RSA4096
                else -> KeyType.RSA
            }
            else -> throw IllegalArgumentException("Unsupported OCI key algorithm: ${shape.algorithm}")
        }

        actual suspend fun generateKey(config: OCIsdkMetadata): OCIKey =
            generateKey(KeyType.secp256r1, config)

        actual suspend fun generateKey(type: KeyType, config: OCIsdkMetadata): OCIKey {
            return retry {
                val provider = config.createAuthProvider()
                val kmsVaultClient = KmsVaultClient.builder().build(provider)
                val vault = getVault(kmsVaultClient, config.vaultId)
                val kmsManagementClient =
                    KmsManagementClient.builder().endpoint(vault.managementEndpoint).build(provider)

                val createKeyDetails = CreateKeyDetails.builder()
                    .keyShape(keyTypeToOciShape(type))
                    .protectionMode(CreateKeyDetails.ProtectionMode.Software)
                    .compartmentId(config.compartmentId)
                    .displayName("WaltKey")
                    .build()

                val response = kmsManagementClient.createKey(
                    CreateKeyRequest.builder().createKeyDetails(createKeyDetails).build()
                )

                val keyId = response.key.id
                val publicKey = getOCIPublicKey(kmsManagementClient, response.key.currentKeyVersion, keyId)

                OCIKey(
                    keyId,
                    config,
                    publicKey.exportJWK(),
                    ociShapeToKeyType(response.key.keyShape)
                )
            }
        }

        suspend fun getOCIPublicKey(
            kmsManagementClient: KmsManagementClient,
            keyVersionId: String,
            keyId: String,
        ): Key {
            val request = GetKeyVersionRequest.builder().keyVersionId(keyVersionId).keyId(keyId).build()
            val response = kmsManagementClient.getKeyVersion(request)
            return JWKKey.importPEM(response.keyVersion.publicKey).getOrThrow()
        }

        fun getKeyVersion(kmsManagementClient: KmsManagementClient, keyId: String): String {
            val response = kmsManagementClient.getKey(GetKeyRequest.builder().keyId(keyId).build())
            return response.key.currentKeyVersion
        }

        fun getVault(kmsVaultClient: KmsVaultClient, vaultId: String?): Vault {
            val response = kmsVaultClient.getVault(GetVaultRequest.builder().vaultId(vaultId).build())
            return response.vault
        }
    }
}

private suspend fun <T> retry(
    maxDuration: Duration = Duration.ofSeconds(2),
    retryInterval: Duration = Duration.ofMillis(100),
    block: suspend () -> T,
): T {
    var result: Result<T>
    var totalDuration = Duration.ZERO

    while (totalDuration < maxDuration) {
        val elapsed = measureTime {
            result = runCatching { block() }
        }

        if (result.isSuccess) {
            log.debug { "OCI operation succeeded after $elapsed" }
            return result.getOrThrow()
        } else {
            totalDuration += elapsed.toJavaDuration()
            if (totalDuration >= maxDuration) {
                throw IllegalStateException(
                    "OCI operation failed after $totalDuration: ${result.exceptionOrNull()?.message}",
                    result.exceptionOrNull()
                )
            }
            delay(retryInterval.toKotlinDuration())
        }
    }
    throw IllegalStateException("OCI operation failed after $totalDuration: retry time limit exceeded")
}