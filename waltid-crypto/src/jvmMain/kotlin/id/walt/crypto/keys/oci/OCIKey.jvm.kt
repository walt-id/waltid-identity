package id.walt.crypto.keys.oci

import com.oracle.bmc.ConfigFileReader
import com.oracle.bmc.auth.AuthenticationDetailsProvider
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider
import com.oracle.bmc.keymanagement.KmsCryptoClient
import com.oracle.bmc.keymanagement.KmsManagementClient
import com.oracle.bmc.keymanagement.KmsVaultClient
import com.oracle.bmc.keymanagement.model.*
import com.oracle.bmc.keymanagement.requests.*
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.OciKeyMeta
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto.utils.JwsUtils.jwsAlg
import io.ktor.util.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.lang.Thread.sleep

@Serializable
@SerialName("oci")
actual class OCIKey actual constructor(
    actual val id: String,
    actual val config: OCIsdkMetadata,

    @Suppress("CanBeParameter", "RedundantSuppression")
    private  var _publicKey: String? ,
    private var _keyType: KeyType? ,

    ) : Key() {

    @Transient
   actual override var keyType: KeyType
        get() = _keyType!!
        set(value) {
            _keyType = value
        }

 actual   override val hasPrivateKey: Boolean
        get() = false

    private suspend fun retrievePublicKey(): Key {
        val getKeyRequest = GetKeyRequest.builder().keyId(id).build()
        val response = kmsManagementClient.getKey(getKeyRequest)
        val publicKey = getOCIPublicKey(kmsManagementClient, response.key.currentKeyVersion, id)
        return publicKey
    }

    actual val configurationFilePath: String = "~/.oci/config"
    actual val profile: String = "DEFAULT"

    @Transient
    private val configFile = ConfigFileReader.parseDefault()
    @Transient
    private val provider = ConfigFileAuthenticationDetailsProvider(configFile)


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


    actual  override fun toString(): String = "[OCI ${keyType.name} key @ ${id}]"


    actual  override suspend fun getKeyId(): String = getPublicKey().getKeyId()


    actual   override suspend fun getThumbprint(): String {
        TODO("Not yet implemented")
    }

    actual override suspend fun exportJWK(): String = throw NotImplementedError("JWK export is not available for remote keys.")

    actual override suspend fun exportJWKObject(): JsonObject = Json.parseToJsonElement(_publicKey!!).jsonObject

    actual override suspend fun exportPEM(): String = throw NotImplementedError("PEM export is not available for remote keys.")


    actual  override suspend fun signRaw(plaintext: ByteArray): ByteArray {

        val signDataDetails =
            SignDataDetails.builder()
                .keyId(id)
                .keyVersionId(getKeyVersion(kmsManagementClient, id))
                .messageType(SignDataDetails.MessageType.Raw)
                .message(plaintext.encodeToBase64Url())
                .signingAlgorithm(SignDataDetails.SigningAlgorithm.EcdsaSha256)
                .build()

        val signRequest =
            SignRequest.builder().signDataDetails(signDataDetails).build()
        val response = kmsCryptoClient.sign(signRequest)
        return response.signedData.signature.encodeToByteArray()
    }

    actual override suspend fun signJws(plaintext: ByteArray, headers: Map<String, String>): String {
        val appendedHeader = HashMap(headers).apply {
            put("alg", "ES256")
        }

        val header = Json.encodeToString(appendedHeader).encodeToByteArray().encodeToBase64Url()
        val payload = plaintext.encodeToBase64Url()

        var rawSignature = signRaw("$header.$payload".encodeToByteArray())

        val signatureBase64Url = rawSignature.encodeToBase64Url()
        val jws ="$header.$payload.$signatureBase64Url"
        return jws
    }

    actual  override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?): Result<ByteArray> {
        check(detachedPlaintext != null) { "An detached plaintext is needed." }
        println("detachedPlaintext: ${detachedPlaintext.decodeToString()}")
        val verifyDataDetails =
            VerifyDataDetails.builder()
                .keyId(id)
                .keyVersionId(getKeyVersion(kmsManagementClient, id))
                .signature(signed.decodeToString())
                .messageType(VerifyDataDetails.MessageType.Raw)
                .message(detachedPlaintext.encodeBase64())
                .signingAlgorithm(VerifyDataDetails.SigningAlgorithm.EcdsaSha256)
                .build()
        val verifyRequest =
            VerifyRequest.builder().verifyDataDetails(verifyDataDetails).build()
        val response = kmsCryptoClient.verify(verifyRequest)
        return response.verifiedData.isSignatureValid.let {
            if (it) Result.success(detachedPlaintext)
            else Result.failure(Exception("Signature verification failed!"))
        }
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

        println("the f signable is: $header.$payload")

        val signable = "$header.$payload".encodeToByteArray()



        return verifyRaw(signature.decodeToString().toByteArray(), signable).map {

            val verifiedPayload = it.decodeToString().substringAfter(".").base64UrlDecode().decodeToString()
            Json.parseToJsonElement(verifiedPayload)

        }
    }

    @Transient
    private var backedKey: Key? = null

    actual  override suspend fun getPublicKey(): Key = backedKey ?: when {
        _publicKey != null -> _publicKey!!.let { JWKKey.importJWK(it).getOrThrow() }
        else -> retrievePublicKey()
    }.also { newBackedKey -> backedKey = newBackedKey }


    actual override suspend fun getPublicKeyRepresentation(): ByteArray = TODO("Not yet implemented")

    actual override suspend fun getMeta(): OciKeyMeta = OciKeyMeta(
        keyId = id,
        keyVersion = getKeyVersion(kmsManagementClient, id),
    )


    actual  companion object {

        actual val DEFAULT_KEY_LENGTH: Int = 32
        // The KeyShape used for testing

        val TEST_KEY_SHAPE: KeyShape = KeyShape.builder().algorithm(KeyShape.Algorithm.Ecdsa).length(DEFAULT_KEY_LENGTH)
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

        actual   suspend fun generateKey(config: OCIsdkMetadata): OCIKey {
            val configurationFilePath: String = "~/.oci/config"
            val profile: String = "DEFAULT"

             val configFile: ConfigFileReader.ConfigFile = ConfigFileReader.parseDefault()

             val provider: AuthenticationDetailsProvider =
                ConfigFileAuthenticationDetailsProvider(configFile)


            // Create KMS clients
             val kmsVaultClient: KmsVaultClient = KmsVaultClient.builder().build(provider)

             val vault: Vault = getVault(kmsVaultClient, config.vaultId)

             val kmsManagementClient: KmsManagementClient =
                KmsManagementClient.builder().endpoint(vault.managementEndpoint).build(provider)

            println("CreateKey Test")
            val createKeyDetails =
                CreateKeyDetails.builder()
                    .keyShape(TEST_KEY_SHAPE)
                    .protectionMode(CreateKeyDetails.ProtectionMode.Software)
                    .compartmentId(config.compartmentId)
                    .displayName("WaltKey")
                    .build()
            val createKeyRequest =
                CreateKeyRequest.builder().createKeyDetails(createKeyDetails).build()
            val response = kmsManagementClient.createKey(createKeyRequest)
            println("Key created: ${response.key}")
            val keyId = response.key.id
            println("Key ID: $keyId")
            val keyVersionId = response.key.currentKeyVersion
            println("Key Version ID: $keyVersionId")
            sleep(5000)
            val publicKey = getOCIPublicKey(kmsManagementClient, keyVersionId, keyId)

            println("Public Key: ${publicKey.exportJWK()}")
            return OCIKey(
                keyId,
                config,
                publicKey.exportJWK(),
                ociKeyToKeyTypeMapping(response.key.keyShape.algorithm.toString().uppercase())
            )
        }


        suspend fun getOCIPublicKey(
            kmsManagementClient: KmsManagementClient,
            keyVersionId: String,
            keyId: String
        ): Key {
            val getKeyRequest = GetKeyVersionRequest.builder().keyVersionId(keyVersionId).keyId(keyId).build()
            val response = kmsManagementClient.getKeyVersion(getKeyRequest)
            val publicKeyPem = response.keyVersion.publicKey
            return JWKKey.importPEM(publicKeyPem)
                .getOrThrow()
        }


        suspend fun getKeyVersion(kmsManagementClient: KmsManagementClient, keyId: String): String {
            val getKeyRequest = GetKeyRequest.builder().keyId(keyId).build()
            val response = kmsManagementClient.getKey(getKeyRequest)
            return response.key.currentKeyVersion
        }

        fun getVault(kmsVaultClient: KmsVaultClient, vaultId: String?): Vault {

            val getVaultRequest = GetVaultRequest.builder().vaultId(vaultId).build()
            val response = kmsVaultClient.getVault(getVaultRequest)

            println("retreive vault: ${response.vault}")

            return response.vault
        }
    }

}

suspend fun main() {

    val compartmentId: String = "ocid1.compartment.oc1..aaaaaaaawirugoz35riiybcxsvf7bmelqsxo3sajaav5w3i2vqowcwqrllxa"
    val vaultId: String =
        "ocid1.vault.oc1.eu-frankfurt-1.entaeh2zaafiy.abtheljss64qlgv6cxm7t4fi5dvfntfbval2ldt6yja3s4niix2hf36defua"


    val config = OCIsdkMetadata(vaultId, compartmentId)
    // val Testkey = oci.generateKey( config)
    val Testkey = OCIKey(
        "ocid1.key.oc1.eu-frankfurt-1.entaeh2zaafiy.abtheljtpq4ytkmzqr3iwkx7dkvrcxsz6ydnsatq6ynfgqdzx2c34d7d3n4a",
        config=config,
        _keyType = KeyType.secp256r1,
        _publicKey = null
    )

//    println("Key ID: ${Testkey.id}")
//    println("Key Type: ${Testkey.keyType}")
//
//    println("key version: ${Testkey.getMeta().keyVersion}")
//
//
//    println("public key: ${Testkey.getPublicKey().exportJWK()}")
//    println("public key: ${Testkey.getPublicKey().exportPEM()}")
//
//    val payload = JsonObject(
//        mapOf(
//            "sub" to JsonPrimitive("16bb17e0-e733-4622-9384-122bc2fc6290"),
//            "iss" to JsonPrimitive("http://localhost:3000"),
//            "aud" to JsonPrimitive("TOKEN"),
//        )
//    ).toString()
    val text = "lqfijrrwgnbizwbfxfvbubnasnltaqku"
    val sign = Testkey.signRaw(text.encodeToByteArray())



    println("Signature with TestKey: ${sign.decodeToString()}")

    val verify = Testkey.verifyRaw(sign, text.encodeToByteArray())
    println("Verify with TestKey: ${verify.isSuccess}")
//
//    println("key for sign JWS: ${Testkey.getPublicKey()}")
//    val signJws = Testkey.signJws(
//        payload.encodeToByteArray()
//    )
//    println("Sign JWS with TestKey: $signJws")

    val jws = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImRpZDpqd2s6ZXlKcmRIa2lPaUpGUXlJc0ltTnlkaUk2SWxBdE1qVTJJaXdpZUNJNklscElSSEJtT1dwSVowMTNkM0ZIU0VSMmNYVnpNbGcxY2t0NWJrbFlRWHBLY25Wek4wcFdjMFYwT0RBaUxDSjVJam9pUVVRd1R6UmtVMEl0UjJORFdVTjJXRmxJVDFjM1JXYzJOVU55Y0dvd2JWOURTSGR1VTFaWmNEZzRNQ0o5IzAifQ.eyJhdWQiOiJodHRwczovL2lzc3Vlci5wb3J0YWwud2FsdC10ZXN0LmNsb3VkIiwiaWF0IjoxNzEzODA1NjM1LCJub25jZSI6IjQwNGU4NjExLTI2ZjgtNGY4Mi05ZDk1LTBkYmUwYmQ4NTUxYyJ9.TUVVQ0lEVmV2NGtMVER5bytKUXdxZGVqUlFCWTBsUkphVDErQzZXVERxWlcyb1k4QWlFQS9TcHp2RU80ODZISGc1MWpOa1BvV1pxWXV3cEtSeC82SEcvUXRXWFJDTHM9"

    val verifyJws = Testkey.verifyJws(jws)
    println("Verify JWS with TestKey: ${if (verifyJws.isSuccess) "Success" else "Failed"}")


}