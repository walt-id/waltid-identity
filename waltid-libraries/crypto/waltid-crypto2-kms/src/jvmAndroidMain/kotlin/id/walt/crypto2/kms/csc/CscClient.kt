package id.walt.crypto2.kms.csc

import id.walt.crypto2.kms.KmsProviderException
import id.walt.crypto2.kms.executeJson
import id.walt.crypto2.kms.executeJsonResponse
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.io.encoding.Base64

internal class CscServiceInfo(val specs: String, val authTypes: List<String>, val oauth2: String?)

internal class CscAuthObject(val type: String, val id: String, val generator: String?)

internal class CscCredentialInfo(
    val keyStatus: String,
    val keyAlgorithms: List<String>,
    val keyCurve: String?,
    val certificateStatus: String?,
    val certificates: List<ByteArray>,
    val authMode: String,
    /** How the [authObjects] combine; `null` means all of them (CSC: "an AND of all authentication objects"). */
    val authExpression: String?,
    val authObjects: List<CscAuthObject>,
)

/** The CSC API v2.2 methods the provider uses. Only synchronous signing; responses are never logged. */
internal class CscClient(private val client: HttpClient) {

    suspend fun info(serviceUrl: String): CscServiceInfo {
        val response = call(serviceUrl, "info", token = null, body = buildJsonObject { })
        return CscServiceInfo(
            specs = response.requiredString("specs"),
            authTypes = response.requiredStrings("authType"),
            oauth2 = response.optionalString("oauth2"),
        )
    }

    suspend fun credentialInfo(serviceUrl: String, token: String, credentialID: String): CscCredentialInfo {
        val response = call(
            serviceUrl,
            "credentials/info",
            token,
            buildJsonObject {
                put("credentialID", credentialID)
                put("certificates", "chain")
                put("certInfo", true)
                put("authInfo", true)
            },
        )
        val key = response.requiredObject("key")
        val cert = response.requiredObject("cert")
        val auth = response.requiredObject("auth")
        val authMode = auth.requiredString("mode")
        return CscCredentialInfo(
            keyStatus = key.requiredString("status"),
            keyAlgorithms = key.requiredStrings("algo"),
            keyCurve = key.optionalString("curve"),
            certificateStatus = cert.optionalString("status"),
            certificates = cert.requiredStrings("certificates").map { decodeBase64(it, "certificate") },
            authMode = authMode,
            authExpression = auth.typedOptionalString("expression"),
            // Required for explicit authorization, where an empty array means that no object is needed.
            authObjects = if (authMode != EXPLICIT) emptyList() else auth.requiredArray("objects").map { element ->
                val objectType = element as? JsonObject
                    ?: throw KmsProviderException("CSC authentication object is not a JSON object")
                CscAuthObject(
                    type = objectType.requiredString("type"),
                    id = objectType.requiredString("id"),
                    generator = objectType.optionalString("generator"),
                )
            },
        )
    }

    /** Explicit credential authorization for one signature over [hash]; returns the SAD. */
    suspend fun authorize(
        serviceUrl: String,
        token: String,
        credentialID: String,
        hash: ByteArray,
        authData: Map<String, String>,
    ): String {
        val response = client.executeJsonResponse(
            provider = PROVIDER,
            endpoint = endpoint(serviceUrl, "credentials/authorize"),
            method = HttpMethod.Post,
            headers = bearer(token),
            contentType = ContentType.Application.Json,
            body = buildJsonObject {
                put("credentialID", credentialID)
                put("numSignatures", 1)
                putJsonArray("hashes") { add(Base64.Default.encode(hash)) }
                put("hashAlgorithmOID", SHA256_OID)
                putJsonArray("authData") {
                    authData.forEach { (id, value) ->
                        addJsonObject {
                            put("id", id)
                            put("value", value)
                        }
                    }
                }
            }.toString(),
        )
        // 202 means an out-of-band step (SMS, app, call) is still underway; polling authorizeCheck is out of scope.
        when (response.status) {
            200 -> Unit
            202 -> throw KmsProviderException(
                "CSC credential authorization needs an out-of-band step, which is not supported", null, response.status
            )
            else -> throw KmsProviderException(
                "CSC credentials/authorize returned unexpected HTTP ${response.status}", null, response.status
            )
        }
        val body = response.body ?: throw KmsProviderException("CSC credentials/authorize returned an empty response")
        return body.requiredString("SAD")
    }

    /** Synchronous `signatures/signHash` for one ECDSA P-256 SHA-256 hash. Never retried: RSSPs bill per signature. */
    suspend fun signHash(serviceUrl: String, token: String, credentialID: String, sad: String, hash: ByteArray): ByteArray {
        val response = call(
            serviceUrl,
            "signatures/signHash",
            token,
            buildJsonObject {
                put("credentialID", credentialID)
                put("SAD", sad)
                putJsonArray("hashes") { add(Base64.Default.encode(hash)) }
                put("hashAlgorithmOID", SHA256_OID)
                put("signAlgo", ECDSA_SHA256_OID)
            },
        )
        val signatures = response.requiredStrings("signatures")
        if (signatures.size != 1) {
            throw KmsProviderException("CSC returned ${signatures.size} signatures for one hash")
        }
        return decodeBase64(signatures.single(), "signature")
    }

    private suspend fun call(serviceUrl: String, method: String, token: String?, body: JsonObject): JsonObject =
        client.executeJson(
            provider = PROVIDER,
            endpoint = endpoint(serviceUrl, method),
            method = HttpMethod.Post,
            headers = token?.let(::bearer).orEmpty(),
            contentType = ContentType.Application.Json,
            body = body.toString(),
        ) ?: throw KmsProviderException("CSC $method returned an empty response")

    private fun endpoint(serviceUrl: String, method: String) = "${serviceUrl.trimEnd('/')}/$method"

    private fun bearer(token: String) = mapOf(HttpHeaders.Authorization to "Bearer $token")

    companion object {
        const val EXPLICIT = "explicit"
        const val SHA256_OID = "2.16.840.1.101.3.4.2.1"
        const val ECDSA_SHA256_OID = "1.2.840.10045.4.3.2"
        const val P256_OID = "1.2.840.10045.3.1.7"
        private const val PROVIDER = "CSC"
        private val base64 = Base64.Default.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

        fun decodeBase64(value: String, name: String): ByteArray = try {
            base64.decode(value)
        } catch (cause: IllegalArgumentException) {
            throw KmsProviderException("CSC returned an invalid base64 $name", cause)
        }
    }
}

private fun JsonObject.optionalString(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf(String::isNotBlank)

private fun JsonObject.requiredString(name: String): String =
    optionalString(name) ?: throw KmsProviderException("CSC response is missing string: $name")

/** Like [optionalString], but a present value that is not a string is an error rather than absent. */
private fun JsonObject.typedOptionalString(name: String): String? {
    val value = this[name]?.takeUnless { it is JsonNull } ?: return null
    val string = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: throw KmsProviderException("CSC response field is not a string: $name")
    return string.takeIf(String::isNotBlank)
}

private fun JsonObject.requiredArray(name: String): JsonArray =
    this[name] as? JsonArray ?: throw KmsProviderException("CSC response is missing array: $name")

private fun JsonObject.requiredObject(name: String): JsonObject =
    this[name] as? JsonObject ?: throw KmsProviderException("CSC response is missing object: $name")

private fun JsonObject.requiredStrings(name: String): List<String> {
    val values = (this[name] as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content }
    if (values.isNullOrEmpty() || values.any { it.isNullOrBlank() }) {
        throw KmsProviderException("CSC response is missing string array: $name")
    }
    return values.filterNotNull()
}
