package id.walt.crypto.keys.oci

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider
import com.oracle.bmc.keymanagement.KmsCryptoClient
import com.oracle.bmc.keymanagement.KmsManagementClient
import com.oracle.bmc.keymanagement.KmsVaultClient
import com.oracle.bmc.keymanagement.model.*
import com.oracle.bmc.keymanagement.requests.*
import id.walt.crypto.keys.EccUtils
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.OciKeyMeta
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.base64Decode
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JvmEccUtils
import id.walt.crypto.utils.JwsUtils.jwsAlg
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*
import org.kotlincrypto.hash.sha2.SHA256
import java.time.Duration
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration


private val log = KotlinLogging.logger { }

@Serializable
@SerialName("oci")
actual class OCIKey actual constructor(
    actual val id: String,
    actual val config: OCIsdkMetadata,

    @Suppress("CanBeParameter", "RedundantSuppression") private var _publicKey: String?,
    private var _keyType: KeyType?,
) : Key() {

    @Transient
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
        val publicKey = getOCIPublicKey(kmsManagementClient, response.key.currentKeyVersion, id)
        return publicKey
    }


    @Transient
    private val provider = InstancePrincipalsAuthenticationDetailsProvider.builder().build()


    // Create KMS clients
    @Transient
    private var kmsVaultClient: KmsVaultClient = KmsVaultClient.builder().build(provider)

    @Transient
    private var vault: Vault = getVault(kmsVaultClient, config.vaultId)

    @Transient
    private var kmsManagementClient: KmsManagementClient =
        KmsManagementClient.builder().endpoint(vault.managementEndpoint).build(provider)

    @Transient
    private var kmsCryptoClient: KmsCryptoClient =
        KmsCryptoClient.builder().endpoint(vault.cryptoEndpoint).build(provider)


    actual override fun toString(): String = "[OCI ${keyType.name} key @ ${config.vaultId}]"


    actual override suspend fun getKeyId(): String = getPublicKey().getKeyId()


    actual override suspend fun getThumbprint(): String {
        TODO("Not yet implemented")
    }

    actual override suspend fun exportJWK(): String =
        throw NotImplementedError("JWK export is not available for remote keys.")

    actual override suspend fun exportJWKObject(): JsonObject = Json.parseToJsonElement(_publicKey!!).jsonObject

    actual override suspend fun exportPEM(): String =
        throw NotImplementedError("PEM export is not available for remote keys.")


    @OptIn(ExperimentalEncodingApi::class)
    actual override suspend fun signRaw(plaintext: ByteArray): ByteArray {
        val encodedMessage: String = Base64.encode(SHA256().digest(plaintext))

        val signDataDetails =
            SignDataDetails.builder().keyId(id).message(encodedMessage).messageType(SignDataDetails.MessageType.Digest)
                .signingAlgorithm(SignDataDetails.SigningAlgorithm.EcdsaSha256)
                .keyVersionId(getKeyVersion(kmsManagementClient, id)).build()

        val signRequest = SignRequest.builder().signDataDetails(signDataDetails).build()
        val response = kmsCryptoClient.sign(signRequest)

        return response.signedData.signature.base64Decode()
    }

    private val _internalJwsAlgorithm by lazy {
        when (keyType) {
            KeyType.Ed25519 -> JWSAlgorithm.EdDSA
            KeyType.secp256k1 -> JWSAlgorithm.ES256K
            KeyType.secp256r1 -> JWSAlgorithm.ES256
            KeyType.RSA -> JWSAlgorithm.RS256 // TODO: RS384 RS512
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    actual override suspend fun signJws(plaintext: ByteArray, headers: Map<String, String>): String {
        val jwsObject = JWSObject(
            JWSHeader.Builder(_internalJwsAlgorithm).customParams(headers).build(), Payload(plaintext)
        )

        val payloadToSign = jwsObject.header.toBase64URL().toString() + '.' + jwsObject.payload.toBase64URL().toString()
        var signed = signRaw(payloadToSign.encodeToByteArray())


        if (keyType in listOf(KeyType.secp256r1, KeyType.secp256k1)) {
            log.trace { "Converted DER to IEEE P1363 signature." }
            val originalSigned = signed

            log.trace { "ORIGINAL (DER) SIGNATURE: ${signed.toHexString()}" }

            signed = EccUtils.convertDERtoIEEEP1363(originalSigned)
            log.trace { "CONVERTED SIGNATURE 1: ${signed.toHexString()}" }

            signed = JvmEccUtils.convertDERtoIEEEP1363BouncyCastle(originalSigned)
            log.trace { "CONVERTED SIGNATURE 2: ${signed.toHexString()}" }
        } else {
            log.trace { "Did not convert DER to IEEE P1363 signature." }
        }

        val encodedSignature = signed.encodeToBase64Url()

        val customJws = "$payloadToSign.${encodedSignature}"

        return customJws

    }

    actual override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?): Result<ByteArray> {

        val verifyDataDetails =
            VerifyDataDetails.builder().keyId(id).message(detachedPlaintext?.encodeToBase64Url())
                .signature(signed.decodeToString())
                .signingAlgorithm(VerifyDataDetails.SigningAlgorithm.EcdsaSha256).build()
        val verifyRequest = VerifyRequest.builder().verifyDataDetails(verifyDataDetails).build()
        val response = kmsCryptoClient.verify(verifyRequest)
        return Result.success(response.verifiedData.isSignatureValid.toString().toByteArray())
    }

    actual override suspend fun verifyJws(signedJws: String): Result<JsonElement> {

        val parts = signedJws.split(".")
        check(parts.size == 3) { "Invalid JWT part count: ${parts.size} instead of 3" }

        val header = parts[0]
        val headers: Map<String, JsonElement> = Json.decodeFromString(header.base64UrlDecode().decodeToString())
        headers["alg"]?.let {
            val algValue = it.jsonPrimitive.content
            check(algValue == keyType.jwsAlg()) { "Invalid key algorithm for JWS: JWS has $algValue, key is ${keyType.jwsAlg()}!" }
        }

        val payload = parts[1]

        val signature = parts[2].base64UrlDecode()

        val signable = "$header.$payload".encodeToByteArray()

        return verifyRaw(signature.decodeToString().toByteArray(), signable).map {

            Json.decodeFromString(it.decodeToString())
        }
    }

    @Transient
    private var backedKey: Key? = null

    actual override suspend fun getPublicKey(): Key = backedKey ?: when {
        _publicKey != null -> _publicKey!!.let { JWKKey.importJWK(it).getOrThrow() }
        else -> retrievePublicKey()
    }.also { newBackedKey -> backedKey = newBackedKey }


    actual override suspend fun getPublicKeyRepresentation(): ByteArray = TODO("Not yet implemented")

    actual override suspend fun getMeta(): OciKeyMeta = OciKeyMeta(
        keyId = id,
        keyVersion = getKeyVersion(kmsManagementClient, id),
    )


    actual companion object {

        actual val DEFAULT_KEY_LENGTH: Int = 32


        val TEST_KEY_SHAPE: KeyShape =
            KeyShape.builder().algorithm(KeyShape.Algorithm.Ecdsa).length(DEFAULT_KEY_LENGTH)
                .curveId(KeyShape.CurveId.NistP256).build()

        private fun keyTypeToOciKeyMapping(type: KeyType) = when (type) {
            KeyType.secp256r1 -> "ECDSA"
            KeyType.RSA -> "RSA"
            KeyType.secp256k1 -> throw IllegalArgumentException("Not supported: $type")
            KeyType.Ed25519 -> throw IllegalArgumentException("Not supported: $type")
        }

        private fun ociKeyToKeyTypeMapping(type: String) = when (type) {
            "ECDSA" -> KeyType.secp256r1
            "RSA" -> KeyType.RSA
            else -> throw IllegalArgumentException("Not supported: $type")
        }

        @OptIn(ExperimentalTime::class)
        actual suspend fun generateKey(config: OCIsdkMetadata): OCIKey {
            return retry {
                val provider = InstancePrincipalsAuthenticationDetailsProvider.builder().build()
                val kmsVaultClient = KmsVaultClient.builder().build(provider)
                val vault = getVault(kmsVaultClient, config.vaultId)
                val kmsManagementClient =
                    KmsManagementClient.builder().endpoint(vault.managementEndpoint).build(provider)


                val createKeyDetails =
                    CreateKeyDetails.builder().keyShape(TEST_KEY_SHAPE)
                        .protectionMode(CreateKeyDetails.ProtectionMode.Software)
                        .compartmentId(config.compartmentId).displayName("WaltKey").build()
                val createKeyRequest = CreateKeyRequest.builder().createKeyDetails(createKeyDetails).build()
                val response = kmsManagementClient.createKey(createKeyRequest)

                val keyId = response.key.id

                val keyVersionId = response.key.currentKeyVersion

                val publicKey = getOCIPublicKey(kmsManagementClient, keyVersionId, keyId)


                OCIKey(
                    keyId,
                    config,
                    publicKey.exportJWK(),
                    ociKeyToKeyTypeMapping(response.key.keyShape.algorithm.toString().uppercase())
                )
            }
        }


        suspend fun getOCIPublicKey(
            kmsManagementClient: KmsManagementClient, keyVersionId: String, keyId: String,
        ): Key {
            val getKeyRequest = GetKeyVersionRequest.builder().keyVersionId(keyVersionId).keyId(keyId).build()
            val response = kmsManagementClient.getKeyVersion(getKeyRequest)
            val publicKeyPem = response.keyVersion.publicKey
            return JWKKey.importPEM(publicKeyPem).getOrThrow()
        }


        fun getKeyVersion(kmsManagementClient: KmsManagementClient, keyId: String): String {
            val getKeyRequest = GetKeyRequest.builder().keyId(keyId).build()
            val response = kmsManagementClient.getKey(getKeyRequest)
            return response.key.currentKeyVersion
        }

        fun getVault(kmsVaultClient: KmsVaultClient, vaultId: String?): Vault {

            val getVaultRequest = GetVaultRequest.builder().vaultId(vaultId).build()
            val response = kmsVaultClient.getVault(getVaultRequest)

            return response.vault
        }
    }

}


@ExperimentalTime
private suspend fun <T> retry(
    maxDuration: Duration = Duration.ofSeconds(2),
    retryInterval: Duration = Duration.ofMillis(100),
    block: suspend () -> T,
): T {
    var result: Result<T>
    var totalDuration = Duration.ZERO

    while (totalDuration < maxDuration) {
        val elapsedTime = measureTime {
            result = runCatching { block() }
        }

        if (result.isSuccess) {
            println("Success after $elapsedTime: ${result.getOrThrow()}")
            return result.getOrThrow()
        } else {
            totalDuration += elapsedTime.toJavaDuration()
            if (totalDuration >= maxDuration) {
                throw IllegalStateException("Failed after total duration of $totalDuration: ${result.exceptionOrNull()?.message}")
            }
            delay(retryInterval.toKotlinDuration())
        }
    }
    throw IllegalStateException("Failed after total duration of $totalDuration: Retry time limit exceeded.")
}

