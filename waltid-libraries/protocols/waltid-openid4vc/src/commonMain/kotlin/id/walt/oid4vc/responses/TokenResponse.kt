package id.walt.oid4vc.responses

import id.walt.crypto.utils.JweUtils
import id.walt.crypto.utils.JwsUtils
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.data.dif.PresentationSubmissionSerializer
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * @param vpToken a JsonElement, according to the [OpenID Spec section 6.1](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-6.1), which defines this as a single string, single JSON object, or a JSON array of strings and/or objects, depending on the presentation format.
 * The conversion to and from this value is aided by the [VpTokenParameter] utility class.
 */
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = TokenResponseSerializer::class)
data class TokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("vp_token") val vpToken: JsonElement? = null,
    @SerialName("id_token") val idToken: String? = null,
    val scope: String? = null,
    @SerialName("c_nonce") val cNonce: String? = null,
    @Serializable(DurationInSecondsSerializer::class)
    @SerialName("c_nonce_expires_in") val cNonceExpiresIn: Duration? = null,
    @SerialName("authorization_pending") val authorizationPending: Boolean? = null,
    val interval: Long? = null,
    @Serializable(PresentationSubmissionSerializer::class)
    @SerialName("presentation_submission") val presentationSubmission: PresentationSubmission? = null,
    val state: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    @SerialName("error_uri") val errorUri: String? = null,
    @Transient val jwsParts: JwsUtils.JwsParts? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject(), IHTTPDataObject {
    val isSuccess get() = accessToken != null || (vpToken != null && presentationSubmission != null)
    override fun toJSON() = Json.encodeToJsonElement(TokenResponseSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<TokenResponse>() {

        override fun fromJSON(jsonObject: JsonObject): TokenResponse =
            Json.decodeFromJsonElement(TokenResponseSerializer, jsonObject)

        fun success(
            accessToken: String,
            tokenType: String,
            expiresIn: Long? = null,
            refreshToken: String? = null,
            scope: String? = null,
            cNonce: String? = null,
            cNonceExpiresIn: Duration? = null,
            authorizationPending: Boolean? = null,
            interval: Long? = null,
            state: String? = null
        ) = TokenResponse(
            accessToken = accessToken,
            tokenType = tokenType,
            expiresIn = expiresIn,
            refreshToken = refreshToken,
            scope = scope,
            cNonce = cNonce,
            cNonceExpiresIn = cNonceExpiresIn,
            authorizationPending = authorizationPending,
            interval = interval,
            state = state
        )

        /**
         * Utility method to construct a success response for a verifiable presentation request
         * @param vpToken Utility object that aids the construction of the parameter value, according [OpenID Spec section 6.1](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-6.1), which defines this as a single string, single JSON object, or a JSON array of strings and/or objects, depending on the presentation format.
         */
        fun success(
            vpToken: VpTokenParameter,
            presentationSubmission: PresentationSubmission?,
            idToken: String?,
            state: String?
        ) =
            TokenResponse(
                vpToken = vpToken.toJsonElement(),
                presentationSubmission = presentationSubmission,
                idToken = idToken,
                state = state
            )

        fun error(
            error: TokenErrorCode,
            errorDescription: String? = null,
            errorUri: String? = null
        ) = TokenResponse(
            error = error.name,
            errorDescription = errorDescription,
            errorUri = errorUri
        )

        private val knownKeys = setOf(
            "access_token",
            "token_type",
            "expires_in",
            "refresh_token",
            "vp_token",
            "scope",
            "c_nonce",
            "c_nonce_expires_in",
            "authorization_pending",
            "interval",
            "presentation_submission",
            "state",
            "error",
            "error_description",
            "error_uri"
        )

        fun fromHttpParameters(parameters: Map<String, List<String>>): TokenResponse {
            if (isDirectPostJWT(parameters)) throw IllegalArgumentException("The given POST parameters are in direct_post.jwt format, use fromDirectPostJwt instead")
            return TokenResponse(
                parameters["access_token"]?.firstOrNull(),
                parameters["token_type"]?.firstOrNull(),
                parameters["expires_in"]?.firstOrNull()?.toLong(),
                parameters["refresh_token"]?.firstOrNull(),
                parameters["vp_token"]?.firstOrNull()?.let { Json.parseToJsonElement(it) },
                parameters["id_token"]?.firstOrNull(),
                parameters["scope"]?.firstOrNull(),
                parameters["c_nonce"]?.firstOrNull(),
                parameters["c_nonce_expires_in"]?.firstOrNull()?.toLong()?.seconds,
                parameters["authorization_pending"]?.firstOrNull()?.toBoolean(),
                parameters["interval"]?.firstOrNull()?.toLong(),
                parameters["presentation_submission"]?.firstOrNull()?.let { PresentationSubmission.fromJSONString(it) },
                parameters["state"]?.firstOrNull(),
                parameters["error"]?.firstOrNull(),
                parameters["error_description"]?.firstOrNull(),
                parameters["error_uri"]?.firstOrNull(),
                null,
                parameters.filter { !knownKeys.contains(it.key) && it.value.isNotEmpty() }
                    .mapValues { Json.parseToJsonElement(it.value.first()) }
            )
        }

        fun isDirectPostJWT(parameters: Map<String, List<String>>) = parameters.containsKey("response")

        fun fromDirectPostJWT(parameters: Map<String, List<String>>, encKeyJwk: JsonObject): TokenResponse {
            if (!isDirectPostJWT(parameters)) throw IllegalArgumentException("The given parameters are not in direct_post.jwt format, use fromHttpParameters instead")

            val response = parameters["response"]!!.first()
            return JweUtils.parseJWE(response, encKeyJwk.toString()).let {
                fromJSON(it.payload).copy(jwsParts = it)
            }
        }
    }

    override fun toHttpParameters(): Map<String, List<String>> {
        return buildMap {
            accessToken?.let { put("access_token", listOf(it)) }
            tokenType?.let { put("token_type", listOf(it)) }
            expiresIn?.let { put("expires_in", listOf(it.toString())) }
            refreshToken?.let { put("refresh_token", listOf(it)) }
            vpToken?.let { put("vp_token", listOf(it.toString())) }
            idToken?.let { put("id_token", listOf(it)) }
            scope?.let { put("scope", listOf(it)) }
            cNonce?.let { put("c_nonce", listOf(it)) }
            cNonceExpiresIn?.let { put("c_nonce_expires_in", listOf(it.inWholeSeconds.toString())) }
            authorizationPending?.let { put("authorization_pending", listOf(it.toString())) }
            interval?.let { put("interval", listOf(it.toString())) }
            presentationSubmission?.let { put("presentation_submission", listOf(it.toJSONString())) }
            state?.let { put("state", listOf(it)) }
            error?.let { put("error", listOf(it)) }
            errorDescription?.let { put("error_description", listOf(it)) }
            errorUri?.let { put("error_uri", listOf(it)) }
            putAll(customParameters!!.mapValues { listOf(it.value.toString()) })
        }
    }

    /**
     * Converts the token response to a direct_post.jwt response, where currently only a JWE-only response is supported.
     */
    fun toDirectPostJWTParameters(
        encKeyJwk: JsonObject,
        alg: String = "ECDH-ES",
        enc: String = "A256GCM",
        headerParams: Map<String, JsonElement> = mapOf()
    ): Map<String, List<String>> {
        return mapOf(
            "response" to listOf(JweUtils.toJWE(toJSON(), encKeyJwk.toString(), alg, enc, headerParams))
        )
    }
}

internal object TokenResponseSerializer : JsonDataObjectSerializer<TokenResponse>(TokenResponse.generatedSerializer())

enum class TokenErrorCode {
    invalid_request,
    invalid_client,
    invalid_grant,
    unauthorized_client,
    unsupported_grant_type,
    invalid_scope,
    server_error
}
