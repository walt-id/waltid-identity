package id.walt.crypto.keys.aws

import id.walt.crypto.exceptions.KeyTypeNotSupportedException
import id.walt.crypto.keys.AwsKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.JwsUtils.jwsAlg
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.util.encodeBase64
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.toByteArray
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.macs.hmac.sha2.HmacSHA256
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

private val logger = KotlinLogging.logger { }

@OptIn(ExperimentalJsExport::class)
@JsExport
@Suppress("TRANSIENT_IS_REDUNDANT")
@Serializable
@SerialName("aws")
class AWSKey(
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
        get() = false

    override fun toString(): String = "[AWS ${keyType.name} key @AWS ${config.region} - $id]"

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

    private val AwsSigningAlgorithm by lazy {
        when (keyType) {
            KeyType.secp256r1 -> "ECDSA_SHA_256"
            KeyType.RSA -> "RSASSA_PSS_SHA_256"
            else -> throw KeyTypeNotSupportedException(keyType.name)
        }
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun signRaw(plaintext: ByteArray): ByteArray {
        val body = """
{
"KeyId":"$id",
"Message":"${plaintext.encodeBase64()}",
"MessageType":"RAW",
"SigningAlgorithm":"$AwsSigningAlgorithm"
}
""".trimIndent().trimMargin()
        println("body : $body")
        val headers = buildSigV4Headers(
            HttpMethod.Post,
            payload = body,
            config = config
        )
        val signature = client.post("https://kms.${config.region}.amazonaws.com/") {
            headers {
                headers.forEach { (key, value) -> append(key, value) } // Append each SigV4 header to the request
                append(HttpHeaders.Host, "kms.${config.region}.amazonaws.com")
                append("X-Amz-Target", "TrentService.Sign") // Specific KMS action for CreateKey
            }
            setBody(body) // Set the JSON body
        }.awsJsonDataBody()
        println("signature : ${signature["Signature"]?.jsonPrimitive?.content}")
        return signature["Signature"]?.jsonPrimitive?.content?.toByteArray() ?: throw Error("failed to sign")

    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun signJws(
        plaintext: ByteArray,
        headers: Map<String, JsonElement>
    ): String {
        val header = Json.encodeToString(mutableMapOf(
            "typ" to "JWT".toJsonElement(),
            "alg" to keyType.jwsAlg().toJsonElement(),
        ).apply { putAll(headers) }).encodeToByteArray().encodeToBase64Url()

        val payload = plaintext.encodeToBase64Url()

        val signable = "$header.$payload"

        val signatureBase64 = signRaw(signable.encodeToByteArray())
        val signatureBase64Url = signatureBase64.encodeToBase64Url()

        return "$signable.$signatureBase64Url"
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?
    ): Result<ByteArray> {
        val body = """
{
"KeyId":"$id",
"Message":"${detachedPlaintext?.encodeBase64()}",
"MessageType":"RAW",
"Signature":"${signed.decodeToString()}",
"SigningAlgorithm":"$AwsSigningAlgorithm"
}
""".trimIndent().trimMargin()
        println("body : $body")
        val headers = buildSigV4Headers(
            HttpMethod.Post,
            payload = body,
            config = config
        )
        val verification = client.post("https://kms.${config.region}.amazonaws.com/") {
            headers {
                headers.forEach { (key, value) -> append(key, value) } // Append each SigV4 header to the request
                append(HttpHeaders.Host, "kms.${config.region}.amazonaws.com")
                append("X-Amz-Target", "TrentService.Verify") // Specific KMS action for CreateKey
            }
            setBody(body) // Set the JSON body
        }.awsJsonDataBody()
        return Result.success(
            verification["SignatureValid"]?.jsonPrimitive?.content?.toByteArray() ?: throw Error("failed to verify")
        )
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        val parts = signedJws.split(".")
        check(parts.size == 3) { "Invalid JWT part count: ${parts.size} instead of 3" }

        val header = parts[0]
        val headers: Map<String, JsonElement> = Json.decodeFromString(header.decodeFromBase64Url().decodeToString())
        headers["alg"]?.let {
            val algValue = it.jsonPrimitive.content
            check(algValue == keyType.jwsAlg()) { "Invalid key algorithm for JWS: JWS has $algValue, key is ${keyType.jwsAlg()}!" }
        }

        val payload = parts[1]
        val signature = parts[2].decodeFromBase64Url()

        val signable = "$header.$payload".encodeToByteArray()

        return verifyRaw(signature, signable).map {
            val verifiedPayload = it.decodeToString().substringAfter(".").decodeFromBase64Url().decodeToString()
            Json.parseToJsonElement(verifiedPayload)
        }
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

    companion object : AWSKeyCreator {
        val client = HttpClient()


        // Utility to hash data using SHA256
        @OptIn(ExperimentalStdlibApi::class)
        fun sha256Hex(data: String): String = SHA256().digest(data.toByteArray()).toHexString()

        // Utility to perform HMAC-SHA256
        fun hmacSHA256(key: ByteArray, data: String): ByteArray =
            HmacSHA256(key).doFinal(data.toByteArray(Charsets.UTF_8))

        // Generate Signature Key
        fun getSignatureKey(config: AWSKeyMetadata, dateStamp: String): ByteArray {
            val kDate = hmacSHA256("AWS4${config.secretAccessKey}".toByteArray(), dateStamp)
            val kRegion = hmacSHA256(kDate, config.region)
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
                "content-type:application/x-amz-json-1.1\nhost:kms.${config.region}.amazonaws.com\nx-amz-date:$amzDate\n"
            val signedHeaders = "content-type;host;x-amz-date"
            val credentialScope = "$dateStamp/${config.region}/kms/aws4_request"

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
                    config.accessKeyId,
                    credentialScope,
                    signedHeaders,
                    signature
                ),
                "x-amz-date" to amzDate,
                "content-type" to "application/x-amz-json-1.1"
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
            val key = client.post("https://kms.${config.region}.amazonaws.com/") {
                headers {
                    headers.forEach { (key, value) -> append(key, value) } // Append each SigV4 header to the request
                    append(HttpHeaders.Host, "kms.${config.region}.amazonaws.com")
                    append("X-Amz-Target", "TrentService.GetPublicKey") // Specific KMS action for ListKeys
                }
                setBody(
                    body
                ) // Set the JSON body
            }.awsJsonDataBody()
            val public = key["PublicKey"]?.jsonPrimitive?.content
            val pem_key = """
-----BEGIN PUBLIC KEY-----
$public
-----END PUBLIC KEY-----
""".trimIndent()

            val keyJWK = JWKKey.importPEM(pem_key)
            return keyJWK.getOrThrow()
        }

        @JvmBlocking
        @JvmAsync
        @JsPromise
        @JsExport.Ignore
        suspend fun list_keys(config: AWSKeyMetadata) {
            val method = HttpMethod.Post
            val headers = buildSigV4Headers(
                method = method,
                payload = """{}""",
                config = config
            )
            val key = client.post("https://kms.${config.region}.amazonaws.com/") {
                headers {
                    headers.forEach { (key, value) -> append(key, value) } // Append each SigV4 header to the request
                    append(HttpHeaders.Host, "kms.${config.region}.amazonaws.com")
                    append("X-Amz-Target", "TrentService.ListKeys") // Specific KMS action for ListKeys
                }
                setBody(
                    """{}"""
                ) // Set the JSON body
            }

            println(key.bodyAsText())


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

        private fun keyTypeToAwsKeyMapping(type: KeyType) = when (type) {
            KeyType.secp256r1 -> "ECC_NIST_P256"
            KeyType.RSA -> "RSA_2048"
            else -> throw KeyTypeNotSupportedException(type.name)
        }

        private fun awsKeyToKeyTypeMapping(type: String) = when (type) {
            "ECC_NIST_P256" -> KeyType.secp256r1
            "RSA_2048" -> KeyType.RSA
            else -> throw KeyTypeNotSupportedException(type)
        }


        @JsExport.Ignore
        override suspend fun generate(type: KeyType, metadata: AWSKeyMetadata): AWSKey {
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
            val key = client.post("https://kms.${metadata.region}.amazonaws.com/") {
                headers {
                    headers.forEach { (key, value) -> append(key, value) } // Append each SigV4 header to the request
                    append(HttpHeaders.Host, "kms.${metadata.region}.amazonaws.com")
                    append("X-Amz-Target", "TrentService.CreateKey") // Specific KMS action for CreateKey
                }
                setBody(body) // Set the JSON body
            }.awsJsonDataBody()

            val KeyId = key["KeyMetadata"]?.jsonObject?.get("KeyId")?.jsonPrimitive?.content
            val publicKey = getPublicKey(metadata, KeyId.toString())


            return AWSKey(
                config = metadata,
                id = KeyId.toString(),
                _publicKey = publicKey.exportJWK(),
                _keyType = awsKeyToKeyTypeMapping(keyType)
            )

        }

    }
}

suspend fun main() {

    val payload = JsonObject(
        mapOf(
            "issuerKey" to JsonObject(
                mapOf(
                    "type" to JsonPrimitive("jwk"),
                    "jwk" to JsonObject(
                        mapOf(
                            "kty" to JsonPrimitive("OKP"),
                            "d" to JsonPrimitive("mDhpwaH6JYSrD2Bq7Cs-pzmsjlLj4EOhxyI-9DM1mFI"),
                            "crv" to JsonPrimitive("Ed25519"),
                            "kid" to JsonPrimitive("Vzx7l5fh56F3Pf9aR3DECU5BwfrY6ZJe05aiWYWzan8"),
                            "x" to JsonPrimitive("T3T4-u1Xz3vAV2JwPNxWfs4pik_JLiArz_WTCvrCFUM")
                        )
                    )
                )
            ),
            "issuerDid" to JsonPrimitive("did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp"),
            "subjectDid" to JsonPrimitive("did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp"),
            "credentialData" to JsonObject(
                mapOf(
                    "@context" to JsonArray(
                        listOf(
                            JsonPrimitive("https://www.w3.org/2018/credentials/v1"),
                            JsonPrimitive("https://www.w3.org/2018/credentials/examples/v1")
                        )
                    ),
                    "id" to JsonPrimitive("http://example.gov/credentials/3732"),
                    "type" to JsonArray(
                        listOf(
                            JsonPrimitive("VerifiableCredential"),
                            JsonPrimitive("UniversityDegreeCredential")
                        )
                    ),
                    "issuer" to JsonObject(
                        mapOf(
                            "id" to JsonPrimitive("did:web:vc.transmute.world")
                        )
                    ),
                    "issuanceDate" to JsonPrimitive("2020-03-10T04:24:12.164Z"),
                    "credentialSubject" to JsonObject(
                        mapOf(
                            "id" to JsonPrimitive("did:example:ebfeb1f712ebc6f1c276e12ec21"),
                            "degree" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("BachelorDegree"),
                                    "name" to JsonPrimitive("Bachelor of Science and Arts")
                                )
                            )
                        )
                    )
                )
            )
        )
    )


    val meta = AWSKeyMetadata(

        accessKeyId = "",
        secretAccessKey = "",
        region = "eu-central-1"
    )
//    list_keys(meta)
    val awsKey = AWSKey.generate(
        metadata = meta,
        type = KeyType.secp256r1
    )
    println("************************************")
    println(awsKey)
    println(awsKey.id)
    println(awsKey.getPublicKey())
    println(awsKey.getPublicKey().exportJWK())
    println(awsKey.keyType)
    println("************************************")

//    val awsKey = AWSKey(
//        config = meta,
//        id = "44d4aa2f-9ee5-4624-ac72-71714d396b7d",
//        _publicKey = "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"rYr7NEc4xSaUfP2_IEyQgMXEJqM7Dql0NHjiVs4flHU\",\"y\":\"mi56CojOhmbQnq8eEUW_X9mlzLVPenkGMELD7hsgUm8\"}",
//        _keyType = KeyType.secp256r1
//    )


    println(awsKey.exportJWKObject())
    val signatureRaw = awsKey.signRaw("hello".toByteArray())
    println("signature $signatureRaw")
    val verificationRaw = awsKey.verifyRaw(signatureRaw, "hello".toByteArray())
    println("verification ${verificationRaw.isSuccess}")


    val signatureJws = awsKey.signJws(payload.toString().toByteArray())
    println("signature $signatureJws")
    val verificationJws = awsKey.verifyJws(signatureJws)
    println("verification ${verificationJws.isSuccess}")

}
