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
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object AuthorizationRequestResolver {
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
        when {
            requestUrl.parameters.contains("request_uri") -> resolveFromRequestUri(requestUrl, http)
            requestUrl.parameters.contains("request") -> resolveFromRequestObject(
                requestUrl.parameters["request"] ?: error("Missing request object"),
            )

            else -> parseParameters(requestUrl.parameters)
        }

    fun parseParameters(parameters: Parameters): AuthorizationRequest =
        json.decodeFromJsonElement(
            AuthorizationRequest.serializer(),
            buildJsonObject {
                parameters.entries().forEach { (key, values) ->
                    values.lastOrNull()?.let { put(key, parseParameterValue(it)) }
                }
            },
        )

    private suspend fun resolveFromRequestUri(requestUrl: Url, http: HttpClient): AuthorizationRequest {
        val requestUri = requireNotNull(requestUrl.parameters["request_uri"]) { "Missing request_uri" }
        val requestUriMethod = requestUrl.parameters["request_uri_method"]?.lowercase()

        val response = when (requestUriMethod) {
            "post" -> http.post(requestUri)
            else -> http.get(requestUri)
        }

        check(response.status.isSuccess()) {
            "AuthorizationRequest cannot be retrieved (${response.status}) from $requestUri: ${response.bodyAsText()}"
        }

        val contentType = response.contentType()
            ?: throw IllegalArgumentException("AuthorizationRequest response does not define a content type")
        val body = response.bodyAsText()

        return when {
            contentType.match("application/oauth-authz-req+jwt") -> resolveFromRequestObject(body)
            contentType.match(ContentType.Application.Json) -> json.decodeFromString<AuthorizationRequest>(body)
            else -> throw IllegalArgumentException("Unsupported AuthorizationRequest content type: $contentType")
        }
    }

    private suspend fun resolveFromRequestObject(requestObject: String): AuthorizationRequest {
        require(requestObject.isJwt()) { "AuthorizationRequest object must be a JWT" }

        val authReqJws = requestObject.decodeJws()
        val jwtAlg = authReqJws.header["alg"]?.jsonPrimitive?.contentOrNull
        if (!jwtAlg.equals("none", ignoreCase = true)) {
            authenticateSignedRequestObject(requestObject, authReqJws.payload)
        }

        return json.decodeFromJsonElement(AuthorizationRequest.serializer(), authReqJws.payload)
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
            is ClientValidationResult.Success -> Unit
            is ClientValidationResult.Failure -> throw SignedAuthorizationRequestValidationException(validationResult.error)
        }
    }

    private fun parseParameterValue(value: String): JsonElement =
        value.takeIf(::looksLikeJsonEncodedParameterValue)
            ?.let { encodedValue ->
                runCatching { json.parseToJsonElement(encodedValue) }
                    .getOrElse { JsonPrimitive(value) }
            }
            ?: JsonPrimitive(value)

    private fun looksLikeJsonEncodedParameterValue(value: String): Boolean {
        val trimmedValue = value.trimStart()
        return trimmedValue.startsWith("{") || trimmedValue.startsWith("[") || trimmedValue.startsWith("\"")
    }
}
