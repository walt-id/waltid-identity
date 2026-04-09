@file:OptIn(ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet

import id.walt.credentials.utils.JwtUtils.isJwt
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientIdPrefixAuthenticator
import id.walt.openid4vp.clientidprefix.ClientIdPrefixParser
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.authorization.RequestUriHttpMethod
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class ResolvedAuthorizationRequest(
    val authorizationRequest: AuthorizationRequest,
    val requestObject: String? = null,
)

object AuthorizationRequestResolver {
    private val log = KotlinLogging.logger { }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }

    class SignedAuthorizationRequestValidationException(
        val clientIdError: ClientIdError,
    ) : IllegalArgumentException(
        "Could not verify signed AuthorizationRequest with client id prefix: ${clientIdError::class.simpleName} - ${clientIdError.message}",
    )

    suspend fun resolve(request: String, http: HttpClient): AuthorizationRequest = resolve(Url(request), http)

    suspend fun resolve(requestUrl: Url, http: HttpClient): AuthorizationRequest =
        resolveDetailed(requestUrl, http).authorizationRequest

    suspend fun resolveDetailed(request: String, http: HttpClient): ResolvedAuthorizationRequest =
        resolveDetailed(Url(request), http)

    suspend fun resolveDetailed(requestUrl: Url, http: HttpClient): ResolvedAuthorizationRequest =
        when {
            requestUrl.parameters.contains("request_uri") -> resolveFromRequestUri(requestUrl, http)

            requestUrl.parameters.contains("request") -> resolveFromRequestObject(requestUrl)

            else -> ResolvedAuthorizationRequest(parseParameters(requestUrl.parameters))
        }

    fun parseParameters(parameters: Parameters): AuthorizationRequest {
        log.trace { "Resolving AuthorizationRequest from direct request parameters" }
        return json.decodeFromJsonElement(
            AuthorizationRequest.serializer(),
            buildJsonObject {
                parameters.entries().forEach { (key, values) ->
                    values.lastOrNull()?.let { put(key, AuthorizationRequestParameterCodec.parse(json, it)) }
                }
            },
        )
    }

    private suspend fun resolveFromRequestUri(requestUrl: Url, http: HttpClient): ResolvedAuthorizationRequest {
        log.trace { "Resolving AuthorizationRequest via request_uri" }

        val requestUri = requireNotNull(requestUrl.parameters["request_uri"]) { "Missing request_uri" }
        val requestUriMethod = parseRequestUriMethod(requestUrl.parameters["request_uri_method"])

        log.trace { "Fetching AuthorizationRequest from request_uri using method ${requestUriMethod?.method ?: "get"}" }
        val response = when (requestUriMethod) {
            null, RequestUriHttpMethod.GET -> http.get(requestUri)
            RequestUriHttpMethod.POST -> http.post(requestUri) {
                contentType(ContentType.Application.FormUrlEncoded)
                accept(ContentType.parse("application/oauth-authz-req+jwt"))
                setBody("")
            }
        }

        val body = response.bodyAsText()
        response.status.run { check(isSuccess()) { "AuthorizationRequest cannot be retrieved ($this) from $requestUri: $body" } }

        val contentType = requireNotNull(response.contentType()) { "AuthorizationRequest response does not define a content type" }
        log.trace { "Resolved AuthorizationRequest response with content type $contentType" }

        return when {
            contentType.match("application/oauth-authz-req+jwt") -> resolveFromRequestObject(body)
            contentType.match(ContentType.Application.Json) -> ResolvedAuthorizationRequest(
                authorizationRequest = json.decodeFromString<AuthorizationRequest>(body),
            )
            else -> throw IllegalArgumentException("Unsupported AuthorizationRequest content type: $contentType")
        }
    }

    private suspend fun resolveFromRequestObject(requestUrl: Url): ResolvedAuthorizationRequest {
        val requestObject = requireNotNull(requestUrl.parameters["request"]) { "Missing request object" }
        return resolveFromRequestObject(requestObject)
    }

    private suspend fun resolveFromRequestObject(requestObject: String): ResolvedAuthorizationRequest {
        log.trace { "Resolving AuthorizationRequest via inline request object" }
        require(requestObject.isJwt()) { "AuthorizationRequest object must be a JWT" }

        val authReqJws = requestObject.decodeJws()
        val jwtAlg = authReqJws.header["alg"]?.jsonPrimitive?.contentOrNull
        if (!jwtAlg.equals("none", ignoreCase = true)) {
            log.trace { "Authenticating signed AuthorizationRequest object" }
            authenticateSignedRequestObject(requestObject, authReqJws.payload)
        }

        return ResolvedAuthorizationRequest(
            authorizationRequest = json.decodeFromJsonElement(AuthorizationRequest.serializer(), authReqJws.payload),
            requestObject = requestObject,
        )
    }

    private suspend fun authenticateSignedRequestObject(requestObject: String, payload: JsonObject) {
        val clientId = requireNotNull(payload["client_id"]?.jsonPrimitive?.contentOrNull) {
            "Missing client_id for signed AuthorizationRequest"
        }
        val clientIdPrefix = ClientIdPrefixParser.parse(clientId)
            .getOrElse { error -> throw IllegalArgumentException("Could not parse client_id prefix: $clientId", error) }
        val clientMetadata = payload["client_metadata"]?.let {
            ClientMetadata.fromJson(it)
                .getOrElse { error -> throw IllegalArgumentException("Could not parse client metadata", error) }
        }

        val context = RequestContext(
            clientId = clientId,
            clientMetadata = clientMetadata,
            requestObjectJws = requestObject,
            redirectUri = payload["redirect_uri"]?.jsonPrimitive?.contentOrNull,
            responseUri = payload["response_uri"]?.jsonPrimitive?.contentOrNull,
        )

        when (val validationResult = ClientIdPrefixAuthenticator.authenticate(clientIdPrefix, context)) {
            is ClientValidationResult.Success -> {
                log.trace { "Signed AuthorizationRequest authentication succeeded for client_id prefix ${clientIdPrefix::class.simpleName}" }
            }

            is ClientValidationResult.Failure -> throw SignedAuthorizationRequestValidationException(validationResult.error)
        }
    }

    private fun parseRequestUriMethod(value: String?): RequestUriHttpMethod? = when (value) {
        null -> null
        RequestUriHttpMethod.GET.method -> RequestUriHttpMethod.GET
        RequestUriHttpMethod.POST.method -> RequestUriHttpMethod.POST
        else -> throw IllegalArgumentException(
            "invalid_request_uri_method: $value is neither 'get' nor 'post'",
        )
    }

}
