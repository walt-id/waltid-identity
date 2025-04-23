package id.walt.oid4vc.requests

import id.walt.oid4vc.OpenID4VC
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.util.JwtUtils
import id.walt.sdjwt.JWTCryptoProvider
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

interface IAuthorizationRequest {
    val responseType: Set<ResponseType>
    val clientId: String
    val responseMode: ResponseMode?
    val redirectUri: String?
    val scope: Set<String>
    val state: String?
    val nonce: String?
}

private val json = Json {
    ignoreUnknownKeys = true
}

@Serializable
data class AuthorizationRequest(
    override val responseType: Set<ResponseType> = setOf(ResponseType.Code),
    override val clientId: String,
    override val responseMode: ResponseMode? = null,
    override val redirectUri: String? = null,
    override val scope: Set<String> = setOf(),
    override val state: String? = null,
    val authorizationDetails: List<AuthorizationDetails>? = null,
    val walletIssuer: String? = null,
    val userHint: String? = null,
    val issuerState: String? = null,
    val requestUri: String? = null,
    val request: String? = null,
    val presentationDefinition: PresentationDefinition? = null,
    val presentationDefinitionUri: String? = null,
    val clientIdScheme: ClientIdScheme? = null,
    val clientMetadata: OpenIDClientMetadata? = null,
    val clientMetadataUri: String? = null,
    override val nonce: String? = null,
    val responseUri: String? = null,
    val codeChallenge: String? = null,
    val codeChallengeMethod: String? = null,
    val claims: JsonObject? = null,
    val idTokenHint: String? = null,
    override val customParameters: Map<String, List<String>> = mapOf()
) : HTTPDataObject(), IAuthorizationRequest {
    val isReferenceToPAR get() = requestUri?.startsWith(OpenID4VC.PUSHED_AUTHORIZATION_REQUEST_URI_PREFIX) ?: false
    override fun toHttpParameters(): Map<String, List<String>> {
        return buildMap {
            put("response_type", listOf(ResponseType.getResponseTypeString(responseType)))
            put("client_id", listOf(clientId))
            responseMode?.let { put("response_mode", listOf(it.toString())) }
            redirectUri?.let { put("redirect_uri", listOf(it)) }
            if (scope.isNotEmpty())
                put("scope", listOf(scope.joinToString(" ")))
            state?.let { put("state", listOf(it)) }
            authorizationDetails?.let {
                put(
                    "authorization_details",
                    listOf(json.encodeToString(AuthorizationDetailsListSerializer, authorizationDetails))
                )
            }
            walletIssuer?.let { put("wallet_issuer", listOf(it)) }
            userHint?.let { put("user_hint", listOf(it)) }
            issuerState?.let { put("issuer_state", listOf(it)) }
            requestUri?.let { put("request_uri", listOf(it)) }
            request?.let { put("request", listOf(it)) }
            presentationDefinition?.let { put("presentation_definition", listOf(it.toJSONString())) }
            presentationDefinitionUri?.let { put("presentation_definition_uri", listOf(it)) }
            clientIdScheme?.let { put("client_id_scheme", listOf(it.value)) }
            clientMetadata?.let { put("client_metadata", listOf(it.toJSONString())) }
            clientMetadataUri?.let { put("client_metadata_uri", listOf(it)) }
            nonce?.let { put("nonce", listOf(it)) }
            responseUri?.let { put("response_uri", listOf(it)) }
            codeChallenge?.let { put("code_challenge", listOf(it)) }
            codeChallengeMethod?.let { put("code_challenge_method", listOf(it)) }
            idTokenHint?.let { put("id_token_hint", listOf(it)) }
            putAll(customParameters)
        }
    }

    /**
     * If response_mode is "direct_post", the response_uri parameter must be used and the redirect_uri parameter must be empty.
     * If response_uri and redirect_uri are empty and client_id_scheme is "redirect_uri", the response_uri or redirect_uri (depending on the response_mode) are taken from the client_id parameter
     */
    fun getRedirectOrResponseUri(): String? {
        return when (responseMode) {
            ResponseMode.direct_post -> responseUri
            else -> redirectUri
        } ?: when (clientIdScheme) {
            ClientIdScheme.RedirectUri -> clientId
            else -> null
        }
    }

    fun toEbsiRequestObjectByReferenceHttpParameters(requestUri: String): Map<String, List<String>> {
        return mapOf(
            "client_id" to listOf(clientId),
            "response_type" to listOf(ResponseType.getResponseTypeString(responseType)),
            "response_mode" to listOf(this.responseMode!!.name),
            "scope" to this.scope.toList(),
            "redirect_uri" to listOf(this.redirectUri!!),
            "presentation_definition_uri" to listOf(this.presentationDefinitionUri!!),
            "request_uri" to listOf(requestUri)
        )
    }

    fun toEbsiRequestObjectByReferenceHttpQueryString(requestUri: String): String {
        return IHTTPDataObject.toHttpQueryString(toEbsiRequestObjectByReferenceHttpParameters(requestUri))
    }

    fun toJSON(): JsonObject {
        return JsonObject(buildMap {
            put("response_type", JsonPrimitive(ResponseType.getResponseTypeString(responseType)))
            put("client_id", JsonPrimitive(clientId))
            responseMode?.let { put("response_mode", JsonPrimitive(it.toString())) }
            redirectUri?.let { put("redirect_uri", JsonPrimitive(it)) }
            if (scope.isNotEmpty())
                put("scope", JsonPrimitive(scope.joinToString(" ")))
            state?.let { put("state", JsonPrimitive(it)) }
            authorizationDetails?.let {
                put(
                    "authorization_details",
                    JsonArray(authorizationDetails.map { it.toJSON() })
                )
            }
            walletIssuer?.let { put("wallet_issuer", JsonPrimitive(it)) }
            userHint?.let { put("user_hint", JsonPrimitive(it)) }
            issuerState?.let { put("issuer_state", JsonPrimitive(it)) }
            requestUri?.let { put("request_uri", JsonPrimitive(it)) }
            request?.let { put("request", JsonPrimitive(it)) }
            presentationDefinition?.let { put("presentation_definition", it.toJSON()) }
            presentationDefinitionUri?.let { put("presentation_definition_uri", JsonPrimitive(it)) }
            clientIdScheme?.let { put("client_id_scheme", JsonPrimitive(it.value)) }
            clientMetadata?.let { put("client_metadata", it.toJSON()) }
            clientMetadataUri?.let { put("client_metadata_uri", JsonPrimitive(it)) }
            nonce?.let { put("nonce", JsonPrimitive(it)) }
            responseUri?.let { put("response_uri", JsonPrimitive(it)) }
            codeChallenge?.let { put("code_challenge", JsonPrimitive(it)) }
            codeChallengeMethod?.let { put("code_challenge_method", JsonPrimitive(it)) }
            idTokenHint?.let { put("id_token_hint", JsonPrimitive(it)) }
            customParameters.forEach { (key, value) ->
                when (value.size) {
                    1 -> put(key, JsonPrimitive(value.first()))
                    else -> put(key, JsonArray(value.map { JsonPrimitive(it) }))
                }
            }
        })
    }

    fun toRequestObject(cryptoProvider: JWTCryptoProvider, keyId: String): String {
        return cryptoProvider.sign(toJSON().addUpdateJsoObject(
            buildJsonObject {
                put("iss", clientId)
                put("aud", "")
                put("exp", (Clock.System.now() + Duration.parse(1.days.toString())).epochSeconds)
            }
        ), keyId)
    }

    fun JsonObject.addUpdateJsoObject(updateJsonObject: JsonObject): JsonObject {
        return JsonObject(
            toMutableMap()
                .apply {
                    updateJsonObject.forEach { (key, je) ->
                        put(key, je)
                    }
                }
        )
    }

    fun toRequestObjectHttpParameters(requestObjectJWT: String): Map<String, List<String>> {
        return mapOf(
            "client_id" to listOf(clientId),
            "request" to listOf(requestObjectJWT)
        )
    }

    fun toRequestObjectByReferenceHttpParameters(requestUri: String): Map<String, List<String>> {
        return mapOf(
            "client_id" to listOf(clientId),
            "request_uri" to listOf(requestUri)
        )
    }

    fun toRequestObjectByReferenceHttpQueryString(requestUri: String): String {
        return IHTTPDataObject.toHttpQueryString(toRequestObjectByReferenceHttpParameters(requestUri))
    }

    companion object : HTTPDataObjectFactory<AuthorizationRequest>() {
        private val knownKeys = setOf(
            "response_type",
            "client_id",
            "redirect_uri",
            "scope",
            "state",
            "authorization_details",
            "wallet_issuer",
            "user_hint",
            "issuer_state",
            "presentation_definition",
            "presentation_definition_uri",
            "client_id_scheme",
            "client_metadata",
            "client_metadata_uri",
            "registration", // compatiblity with OIDC4VP 1.0.10 - replaced by client_metadata
            "registration_uri", // compatiblity with OIDC4VP 1.0.10 - replaced by client_metadata_uri
            "nonce",
            "response_mode",
            "response_uri",
            "code_challenge",
            "code_challenge_method",
            "id_token_hint",
            "claims"
        )

        suspend fun fromRequestObjectByReference(requestUri: String): AuthorizationRequest {
            val body = id.walt.oid4vc.util.http.get(requestUri).bodyAsText()

            return fromRequestObject(body)
        }

        fun fromRequestObject(request: String): AuthorizationRequest {
            return fromJSON(JwtUtils.parseJWTPayload(request))
        }

        fun fromJSON(requestObj: JsonObject): AuthorizationRequest {
            return fromHttpParameters(requestObj.mapValues { e ->
                when (e.value) {
                    is JsonArray -> e.value.jsonArray.map { it.toString() }.toList()
                    is JsonPrimitive -> listOf(e.value.jsonPrimitive.content)
                    else -> listOf(e.value.jsonObject.toString())
                }
            })
        }

        suspend fun fromHttpParametersAuto(parameters: Map<String, List<String>>): AuthorizationRequest {
            return when {
                parameters.containsKey("response_type") && parameters.containsKey("client_id") -> fromHttpParameters(parameters)
                parameters.containsKey("request_uri") -> fromRequestObjectByReference(parameters["request_uri"]!!.first())
                parameters.containsKey("request") -> fromRequestObject(parameters["request"]!!.first())
                else -> throw Exception("Could not find request parameters or object in given parameters")
            }
        }

        override fun fromHttpParameters(parameters: Map<String, List<String>>): AuthorizationRequest {
            return AuthorizationRequest(
                parameters["response_type"]!!.first().let { ResponseType.fromResponseTypeString(it) },
                parameters["client_id"]!!.first(),
                parameters["response_mode"]?.firstOrNull()?.let { ResponseMode.fromString(it) },
                parameters["redirect_uri"]?.firstOrNull(),
                parameters["scope"]?.flatMap { it.split(" ") }?.toSet() ?: setOf(),
                parameters["state"]?.firstOrNull(),
                parameters["authorization_details"]?.flatMap {
                    json.decodeFromString(
                        AuthorizationDetailsListSerializer,
                        it
                    )
                },
                parameters["wallet_issuer"]?.firstOrNull(),
                parameters["user_hint"]?.firstOrNull(),
                parameters["issuer_state"]?.firstOrNull(),
                parameters["request_uri"]?.firstOrNull(),
                parameters["request"]?.firstOrNull(),
                parameters["presentation_definition"]?.firstOrNull()?.let { PresentationDefinition.fromJSONString(it) },
                parameters["presentation_definition_uri"]?.firstOrNull(),
                parameters["client_id_scheme"]?.firstOrNull()?.let { ClientIdScheme.fromValue(it) },
                (parameters["client_metadata"] ?: parameters["registration"])?.firstOrNull()?.let {
                    OpenIDClientMetadata.fromJSONString(it)
                },
                (parameters["client_metadata_uri"] ?: parameters["registration_uri"])?.firstOrNull(),
                parameters["nonce"]?.firstOrNull(),
                parameters["response_uri"]?.firstOrNull(),
                parameters["code_challenge"]?.firstOrNull(),
                parameters["code_challenge_method"]?.firstOrNull(),
                parameters["claims"]?.firstOrNull()?.let { json.decodeFromString(it) },
                parameters["id_token_hint"]?.firstOrNull(),
                parameters.filterKeys { !knownKeys.contains(it) }
            )
        }
    }

}
