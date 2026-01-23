@file:OptIn(ExperimentalTime::class)

package id.walt.crypto.keys.aws

import id.walt.crypto.exceptions.KeyNotFoundException
import id.walt.crypto.exceptions.KeyTypeNotSupportedException
import id.walt.crypto.exceptions.SigningException
import id.walt.crypto.exceptions.VerificationException
import id.walt.crypto.keys.*
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.macs.hmac.sha2.HmacSHA256
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val logger = KotlinLogging.logger { }

data class AWSAuthConfiguration(
    val accessKeyId: String?,
    val secretAccessKey: String?,
    val region: String?,
    val sessionToken: String?,
    val expiration: String?,
    val roleName: String? = null
)

var _accessAWS: AWSAuthConfiguration? = null
var timeoutAt: Instant? = null


@OptIn(ExperimentalJsExport::class)
@JsExport
@Suppress("TRANSIENT_IS_REDUNDANT")
@Serializable
@SerialName("aws-rest-api")
class AWSKeyRestAPI(
    val config: AWSKeyMetadata,
    val id: String,
    private var _publicKey: String? = null,
    private var _keyType: KeyType? = null
) : Key() {


    override var keyType: KeyType
        get() = _keyType!!
        set(value) {
            _keyType = value
        }

    override val hasPrivateKey: Boolean
        get() = true

    override fun toString(): String = "[AWS ${keyType.name} key @AWS ${config.auth.region} - $id]"

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getKeyId(): String = getPublicKey().getKeyId()

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getThumbprint(): String =
        throw NotImplementedError("Thumbprint is not available for remote keys.")

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun exportJWK(): String = throw NotImplementedError("JWK export is not available for remote keys.")

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun exportJWKObject(): JsonObject = Json.parseToJsonElement(_publicKey!!).jsonObject

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
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

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray {
        if (!awsSigningAlgorithm.endsWith("_SHA_256")){
            throw SigningException("failed to sign - unsupported hashing algorithm: $awsSigningAlgorithm")
        }
        val digestedMessage = sha256(plaintext)

        val body = """
{
"KeyId":"$id",
"Message":"${digestedMessage.encodeBase64()}",
"MessageType":"DIGEST",
"SigningAlgorithm":"$awsSigningAlgorithm"
}
""".trimIndent().trimMargin()
        val headers = buildSigV4Headers(
            HttpMethod.Post,
            payload = body,
            config = config
        )

        val awsKmsUrl = "kms.${config.auth.region}.amazonaws.com"

        logger.debug { "Calling AWS KMS ($awsKmsUrl) - TrentService.Sign" }

        val signature = client.post("https://$awsKmsUrl/") {
            headers {
                headers.forEach { (key, value) -> append(key, value) } // Append each SigV4 header to the request
                append(HttpHeaders.Host, "kms.${config.auth.region}.amazonaws.com")
                append("X-Amz-Target", "TrentService.Sign") // Specific KMS action for CreateKey
                _accessAWS?.sessionToken?.takeIf { it.isNotEmpty() }?.let {
                    append("X-Amz-Security-Token", it)
                }
            }
            setBody(body) // Set the JSON body
        }.awsJsonDataBody()
        return signature["Signature"]?.jsonPrimitive?.content?.decodeFromBase64()
            ?: throw SigningException("failed to sign")
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun signJws(
        plaintext: ByteArray,
        headers: Map<String, JsonElement>
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

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verifyRaw(signed: ByteArray, detachedPlaintext: ByteArray?, customSignatureAlgorithm: String?): Result<ByteArray> {
        val messageToVerify = detachedPlaintext ?: return Result.failure(IllegalArgumentException("Detached plaintext is required for verification"))

        // Calculate SHA-256 hash to handle payloads larger than 4KB
        val digestedMessage = sha256(messageToVerify)

        val body = """
{
"KeyId":"$id",
"Message":"${digestedMessage.encodeBase64()}",
"MessageType":"DIGEST",
"Signature":"${signed.encodeBase64()}",
"SigningAlgorithm":"$awsSigningAlgorithm"
}
""".trimIndent().trimMargin()
        val headers = buildSigV4Headers(
            HttpMethod.Post,
            payload = body,
            config = config
        )

        val awsKmsUrl = "kms.${config.auth.region}.amazonaws.com"

        logger.debug { "Calling AWS KMS ($awsKmsUrl) - TrentService.Verify" }

        val verification = client.post("https://$awsKmsUrl/") {
            headers {
                headers.forEach { (key, value) -> append(key, value) } // Append each SigV4 header to the request
                append(HttpHeaders.Host, awsKmsUrl)
                append("X-Amz-Target", "TrentService.Verify") // Specific KMS action for CreateKey
                _accessAWS?.sessionToken?.takeIf { it.isNotEmpty() }?.let {
                    append("X-Amz-Security-Token", it)
                }
            }
            setBody(body) // Set the JSON body
        }.awsJsonDataBody()
        return Result.success(
            verification["SignatureValid"]?.jsonPrimitive?.content?.decodeFromBase64()
                ?: throw VerificationException("failed to verify")
        )
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        val publicKey = getPublicKey()
        val verification = publicKey.verifyJws(signedJws)
        return verification
    }

    @Transient
    private var backedKey: Key? = null

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getPublicKey(): Key = backedKey ?: when {
        _publicKey != null -> _publicKey!!.let {
            JWKKey.importJWK(it).getOrThrow()
        }

        else -> getPublicKey()
    }.also { newBackedKey -> backedKey = newBackedKey }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getPublicKeyRepresentation(): ByteArray {
        TODO("Not yet implemented")
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun getMeta(): AwsKeyMeta = AwsKeyMeta(getKeyId())

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun deleteKey(): Boolean {
        val body = """
{
"KeyId":"$id",
"PendingWindowInDays":7
}
""".trimIndent().trimMargin()
        val headers = buildSigV4Headers(
            HttpMethod.Post,
            payload = body,
            config = config
        )

        val awsKmsUrl = "kms.${config.auth.region}.amazonaws.com"

        logger.debug { "Calling AWS KMS ($awsKmsUrl) - TrentService.ScheduleKeyDeletion" }

        val response = client.post("https://$awsKmsUrl/") {
            headers {
                headers.forEach { (key, value) -> append(key, value) } // Append each SigV4 header to the request
                append(HttpHeaders.Host, awsKmsUrl)
                append("X-Amz-Target", "TrentService.ScheduleKeyDeletion") // Specific KMS action for CreateKey
            }
            setBody(body)
        }
        logger.debug { "Key $id scheduled for deletion" }
        return response.status == HttpStatusCode.OK
    }



    companion object : AWSKeyCreator {
        private val client = HttpClient()

        @JsExport.Ignore
        suspend fun authAccess(config: AWSKeyMetadata) {
            val isAccessDataProvided =
                config.auth.accessKeyId?.isNotEmpty() == true && config.auth.secretAccessKey?.isNotEmpty() == true

            if (isAccessDataProvided) {
                _accessAWS = AWSAuthConfiguration(
                    config.auth.accessKeyId,
                    config.auth.secretAccessKey,
                    config.auth.region,
                    null,
                    null,
                    null
                )
                timeoutAt = null
            } else {
                val token = getIMDSv2Token()
                val actualRoleName = getRoleName(token)
                val providedRoleName = config.auth.roleName


                if (providedRoleName?.isNotEmpty() == true && providedRoleName != actualRoleName) {
                    throw IllegalArgumentException(
                        "Role name mismatch please check the role name provided."
                    )
                }
                _accessAWS = getTemporaryCredentials(token, providedRoleName.toString(), config.auth.region.toString())
                timeoutAt = Clock.System.now().plus(3600.seconds)
            }
        }

        @JsExport.Ignore
        suspend fun getAccess(config: AWSKeyMetadata): AWSAuthConfiguration? {
            if (_accessAWS == null || (timeoutAt != null && timeoutAt!! <= Clock.System.now()) || config.auth.roleName != _accessAWS?.roleName) {
                authAccess(config)
            }

            return _accessAWS
        }


        // Utility to hash data using SHA256
        @OptIn(ExperimentalStdlibApi::class)
        fun sha256Hex(data: String): String = SHA256().digest(data.toByteArray()).toHexString()

        // Utility to perform HMAC-SHA256
        fun hmacSHA256(key: ByteArray, data: String): ByteArray =
            HmacSHA256(key).doFinal(data.toByteArray(Charsets.UTF_8))

        // Utility to perform SHA-256 digest on binary data
        fun sha256(data: ByteArray): ByteArray = SHA256().digest(data)

        // Generate Signature Key
        fun getSignatureKey(config: AWSKeyMetadata, dateStamp: String): ByteArray {
            val kDate = hmacSHA256(
                "AWS4${_accessAWS?.secretAccessKey ?: config.auth.secretAccessKey!!}".toByteArray(),
                dateStamp
            )
            val kRegion = hmacSHA256(kDate, config.auth.region.toString())
            val kService = hmacSHA256(kRegion, "kms")
            return hmacSHA256(kService, "aws4_request")
        }

        // Prepare canonical request
        fun createCanonicalRequest(
            method: HttpMethod,
            canonicalUri: String,
            canonicalQueryString: String,
            canonicalHeaders: String,
            signedHeaders: String,
            payload: String
        ): String {
            val payloadHash = sha256Hex(payload)
            return """${method.value}
$canonicalUri
$canonicalQueryString
$canonicalHeaders
$signedHeaders
$payloadHash
""".trimIndent().trimMargin()
        }

        // Prepare string to sign
        fun createStringToSign(
            algorithm: String,
            amzDate: String,
            credentialScope: String,
            canonicalRequest: String
        ): String {
            return """$algorithm
$amzDate
$credentialScope
${sha256Hex(canonicalRequest)}
""".trimIndent().trimMargin()
        }

        // Generate the final signature
        @OptIn(ExperimentalStdlibApi::class)
        fun generateSignature(signingKey: ByteArray, stringToSign: String): String {
            return hmacSHA256(signingKey, stringToSign).toHexString()
        }

        // Construct Authorization Header
        fun createAuthorizationHeader(
            algorithm: String,
            accessKey: String,
            credentialScope: String,
            signedHeaders: String,
            signature: String
        ): String {
            return "$algorithm Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"
        }

        // Build the SigV4 headers
        fun buildSigV4Headers(
            method: HttpMethod,
            payload: String,
            config: AWSKeyMetadata
        ): Map<String, String> {
            val currentDateTime = Clock.System.now().toLocalDateTime(TimeZone.UTC)
            val dateStamp = currentDateTime.date.toString().replace("-", "")
            val amzDate = currentDateTime.toInstant(TimeZone.UTC).toString().replace("-", "").replace(":", "")
                .substring(0, 15) + "Z"

            val canonicalUri = "/"
            val canonicalQueryString = ""
            val canonicalHeaders =
                "content-type:application/x-amz-json-1.1\nhost:kms.${config.auth.region}.amazonaws.com\nx-amz-date:$amzDate\n"
            val signedHeaders = "content-type;host;x-amz-date"
            val credentialScope = "$dateStamp/${config.auth.region}/kms/aws4_request"
            val canonicalRequest = createCanonicalRequest(
                method, canonicalUri, canonicalQueryString, canonicalHeaders, signedHeaders, payload
            )
            val stringToSign = createStringToSign(
                "AWS4-HMAC-SHA256", amzDate, credentialScope, canonicalRequest
            )

            val signingKey = getSignatureKey(config, dateStamp)
            val signature = generateSignature(signingKey, stringToSign)

            return mapOf(
                "Authorization" to createAuthorizationHeader(
                    "AWS4-HMAC-SHA256",
                    _accessAWS?.accessKeyId ?: config.auth.accessKeyId!!,
                    credentialScope,
                    signedHeaders,
                    signature
                ),
                "x-amz-date" to amzDate,
                "content-type" to "application/x-amz-json-1.1"
            )
        }


        // Function to get IMDSv2 token
        @JsExport.Ignore
        suspend fun getIMDSv2Token(ttlSeconds: Int = 21600): String {
            val url = "http://169.254.169.254/latest/api/token"
            val token = client.put(url) {
                headers {
                    append("X-aws-ec2-metadata-token-ttl-seconds", ttlSeconds.toString())
                }
            }
            logger.trace { "AWS TOKEN: $token" }
            return token.bodyAsText()
        }

        // Function to get role name
        @JsExport.Ignore
        suspend fun getRoleName(token: String): String {
            val url = "http://169.254.169.254/latest/meta-data/iam/security-credentials/"
            val roleName = client.get(url) {
                headers {
                    append("X-aws-ec2-metadata-token", token)
                }
            }
            logger.debug { "AWS Role Name: $roleName" }
            return roleName.bodyAsText()
        }

        // Function to get temporary credentials using role name
        @JsExport.Ignore
        suspend fun getTemporaryCredentials(token: String, roleName: String, region: String): AWSAuthConfiguration {
            val url = "http://169.254.169.254/latest/meta-data/iam/security-credentials/$roleName"

            val response = client.get(url) {
                headers {
                    append("X-aws-ec2-metadata-token", token)
                }
            }

            if (response.status != HttpStatusCode.OK) {
                throw IllegalArgumentException("AWS server returned an invalid response: ${response.status} - please check the role name and region")
            }


            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject


            val accessKeyId =
                json["AccessKeyId"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("AccessKeyId not found")
            val secretAccessKey = json["SecretAccessKey"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("SecretAccessKey not found")
            val sessionToken =
                json["Token"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Token not found")
            val expiration =
                json["Expiration"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Expiration not found")

            return AWSAuthConfiguration(
                accessKeyId = accessKeyId,
                secretAccessKey = secretAccessKey,
                region = region,
                sessionToken = sessionToken,
                expiration = expiration,
                roleName = roleName
            )
        }


        @JvmBlocking
        @JvmAsync
        @JsPromise
        @JsExport.Ignore
        @OptIn(ExperimentalEncodingApi::class)
        suspend fun getPublicKey(config: AWSKeyMetadata, keyId: String): Key {
            val method = HttpMethod.Post
            val body = """
{
"KeyId": "$keyId"
}
""".trimIndent().trimMargin()
            val headers = buildSigV4Headers(
                method = method,
                payload = body,
                config = config
            )

            val awsKmsUrl = "kms.${config.auth.region}.amazonaws.com"

            logger.debug { "Calling AWS KMS ($awsKmsUrl) - TrentService.GetPublicKey" }

            val key = client.post("https://$awsKmsUrl/") {
                headers {
                    headers.forEach { (key, value) -> append(key, value) } // Append each SigV4 header to the request
                    append(HttpHeaders.Host, awsKmsUrl)
                    append("X-Amz-Target", "TrentService.GetPublicKey") // Specific KMS action for ListKeys
                    _accessAWS?.sessionToken?.takeIf { it.isNotEmpty() }?.let {
                        append("X-Amz-Security-Token", it)
                    }
                }
                setBody(
                    body
                ) // Set the JSON body
            }.awsJsonDataBody()

            val public = key["PublicKey"]?.jsonPrimitive?.content

            if (public.isNullOrEmpty()) throw KeyNotFoundException(message = "Could not determine PublicKey")

            val pemKey = """
-----BEGIN PUBLIC KEY-----
$public
-----END PUBLIC KEY-----
""".trimIndent()

            val keyJWK = JWKKey.importPEM(pemKey)
            return keyJWK.getOrThrow()
        }

        private suspend fun HttpResponse.awsJsonDataBody(): JsonObject {
            val baseMsg = { "AWS server (URL: ${this.request.url}) returned an invalid response: " }

            return runCatching {
                // First, get the body as a string
                val bodyStr = this.bodyAsText()

                // Parse the string as JsonObject
                Json.parseToJsonElement(bodyStr).jsonObject
            }.getOrElse {
                val bodyStr = this.bodyAsText() // Get the body in case of an exception
                throw IllegalArgumentException(
                    baseMsg.invoke() + if (bodyStr.isEmpty()) "empty response (instead of JSON data)"
                    else "invalid response: $bodyStr"
                )
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


        @JsExport.Ignore
        override suspend fun generate(type: KeyType, metadata: AWSKeyMetadata): AWSKeyRestAPI {

            if (metadata.auth.accessKeyId.isNullOrBlank() && metadata.auth.secretAccessKey.isNullOrBlank()) {
                getAccess(metadata)
            }


            val keyType = keyTypeToAwsKeyMapping(type)
            val body =
                """{
"KeySpec":"$keyType",
"KeyUsage":"SIGN_VERIFY"
}
""".trimIndent().trimMargin()
            val headers = buildSigV4Headers(
                method = HttpMethod.Post,
                payload = body,
                config = metadata
            )
            val awsKmsUrl = "kms.${metadata.auth.region}.amazonaws.com"

            logger.debug { "Calling AWS KMS ($awsKmsUrl) - TrentService.CreateKey" }
            val key = client.post("https://$awsKmsUrl/") {
                headers {
                    headers.forEach { (key, value) -> append(key, value) } // Append each SigV4 header to the request
                    append(HttpHeaders.Host, awsKmsUrl)
                    append("X-Amz-Target", "TrentService.CreateKey") // Specific KMS action for CreateKey
                    _accessAWS?.sessionToken?.takeIf { it.isNotEmpty() }?.let {
                        append("X-Amz-Security-Token", it)
                    }
                }
                setBody(body) // Set the JSON body
            }.awsJsonDataBody()

            val keyId = key["KeyMetadata"]?.jsonObject?.get("KeyId")?.jsonPrimitive?.content

            if (keyId.isNullOrEmpty()) throw KeyNotFoundException(message = "Key ID could not be determined")

            val publicKey = getPublicKey(metadata, keyId.toString())

            return AWSKeyRestAPI(
                config = metadata,
                id = keyId.toString(),
                _publicKey = publicKey.exportJWK(),
                _keyType = awsKeyToKeyTypeMapping(keyType)
            )
        }

    }
}

