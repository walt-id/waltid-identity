package id.walt.webwallet.service.exchange

import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.credentials.utils.JwtUtils.isJwt
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.DcqlQuery
import id.walt.openid4vp.clientidprefix.ClientIdPrefixAuthenticator
import id.walt.openid4vp.clientidprefix.ClientIdPrefixParser
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.credentials.CredentialFilterObject
import id.walt.webwallet.service.credentials.CredentialsService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, ExperimentalSerializationApi::class)
class OpenId4VpPresentationService(
    private val http: HttpClient,
    private val credentialService: CredentialsService,
) {
    private val logger = KotlinLogging.logger { }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }

    suspend fun tryResolveAuthorizationRequest(request: String): Result<AuthorizationRequest> = runCatching {
        val requestUrl = Url(request)
        when {
            requestUrl.parameters.contains("request_uri") -> resolveAuthorizationRequestFromRequestUri(requestUrl)
            requestUrl.parameters.contains("request") -> resolveAuthorizationRequestFromRequestObject(
                requestUrl.parameters["request"] ?: error("Missing request object"),
            )

            else -> parseAuthorizationRequestParameters(requestUrl.parameters)
        }
    }

    suspend fun resolvePresentationRequest(request: String): String {
        val requestUrl = Url(request)
        val authorizationRequest = tryResolveAuthorizationRequest(request).getOrThrow()
        return authorizationRequest.toHttpUrl(baseUrlBuilder(requestUrl)).toString()
    }

    fun buildWalletPresentationRequest(request: String, resolvedRequest: AuthorizationRequest): Url {
        val requestUrl = Url(request)
        return resolvedRequest.toWalletPresentUrl(baseUrlBuilder(requestUrl))
    }

    suspend fun matchCredentialsForPresentationRequest(
        walletId: Uuid,
        request: String,
        selectedCredentialIds: Set<String>? = null,
    ): List<WalletCredential> =
        tryResolveAuthorizationRequest(request)
            .getOrThrow()
            .dcqlQuery?.let { query ->
                matchCredentials(
                    query = query,
                    credentials = credentialService.list(walletId, CredentialFilterObject.default),
                    selectedCredentialIds = selectedCredentialIds,
                )
            } ?: emptyList()

    suspend fun matchCredentials(
        query: DcqlQuery,
        credentials: List<WalletCredential>,
        selectedCredentialIds: Set<String>? = null,
    ): List<WalletCredential> {
        val matched = matchCredentialResults(query, credentials, selectedCredentialIds)
        val matchedCredentialIds = matched.values
            .flatten()
            .mapNotNull { (it.credential as? RawDcqlCredential)?.id }
            .distinct()

        return credentials.filter { credential -> credential.id in matchedCredentialIds }
    }

    suspend fun matchCredentialResults(
        query: DcqlQuery,
        credentials: List<WalletCredential>,
        selectedCredentialIds: Set<String>? = null,
    ): Map<String, List<DcqlMatcher.DcqlMatchResult>> {
        val dcqlCredentials = credentials
            .filter { selectedCredentialIds == null || it.id in selectedCredentialIds }
            .mapNotNull { it.toDcqlCredentialOrNull() }

        if (dcqlCredentials.isEmpty()) {
            return emptyMap()
        }

        val matched = DcqlMatcher.match(query, dcqlCredentials).getOrElse { error ->
            logger.info(error) { "OpenID4VP credential matching returned no matches" }
            return emptyMap()
        }
        return matched
    }

    private suspend fun resolveAuthorizationRequestFromRequestUri(requestUrl: Url): AuthorizationRequest {
        val requestUri = requireNotNull(requestUrl.parameters["request_uri"]) { "Missing request_uri" }
        val requestUriMethod = requestUrl.parameters["request_uri_method"]?.lowercase()

        val response = when (requestUriMethod) {
            "post" -> http.post(requestUri)
            else -> http.get(requestUri)
        }

        check(response.status.value in 200..299) {
            "AuthorizationRequest cannot be retrieved (${response.status}) from $requestUri: ${response.bodyAsText()}"
        }

        val contentType = response.contentType()
            ?: throw IllegalArgumentException("AuthorizationRequest response does not define a content type")
        val body = response.bodyAsText()

        return when {
            contentType.match("application/oauth-authz-req+jwt") -> resolveAuthorizationRequestFromRequestObject(body)
            contentType.match(ContentType.Application.Json) -> json.decodeFromString<AuthorizationRequest>(body)
            else -> throw IllegalArgumentException("Unsupported AuthorizationRequest content type: $contentType")
        }
    }

    private suspend fun resolveAuthorizationRequestFromRequestObject(requestObject: String): AuthorizationRequest {
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
            is ClientValidationResult.Failure -> throw IllegalArgumentException(
                "Could not verify signed AuthorizationRequest with client id prefix: ${validationResult.error::class.simpleName} - ${validationResult.error.message}",
            )
        }
    }

    private fun parseAuthorizationRequestParameters(parameters: Parameters): AuthorizationRequest =
        json.decodeFromJsonElement(
            AuthorizationRequest.serializer(),
            buildJsonObject {
                parameters.entries().forEach { (key, values) ->
                    values.lastOrNull()?.let { put(key, parseParameterValue(it)) }
                }
            },
        )

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

    private fun baseUrlBuilder(requestUrl: Url) = URLBuilder(requestUrl.toString().substringBefore("?"))

    private suspend fun WalletCredential.toDcqlCredentialOrNull(): RawDcqlCredential? =
        runCatching {
            val rawCredential = buildRawCredential()
            val digitalCredential = CredentialParser.parseOnly(rawCredential)

            RawDcqlCredential(
                id = id,
                format = digitalCredential.format,
                data = digitalCredential.credentialData,
                disclosures = digitalCredential.toDcqlDisclosures(),
                originalCredential = digitalCredential,
            )
        }.onFailure { error ->
            logger.warn(error) { "Skipping wallet credential $id while building DCQL input" }
        }.getOrNull()

    private fun WalletCredential.buildRawCredential(): String =
        document + disclosures
            ?.takeIf { it.isNotBlank() }
            ?.let { "~$it" }
            .orEmpty()

    private fun DigitalCredential.toDcqlDisclosures(): List<DcqlDisclosure>? =
        (this as? SelectivelyDisclosableVerifiableCredential)
            ?.disclosures
            ?.map { DcqlDisclosure(it.name, it.value) }

    private fun AuthorizationRequest.toWalletPresentUrl(baseUrl: URLBuilder): Url {
        val encodedValues = json.encodeToJsonElement(AuthorizationRequest.serializer(), this).jsonObject
            .entries
            .filterNot { it.value == JsonNull }

        val urlBuilder = URLBuilder(baseUrl.build())
        encodedValues.forEach { (key, value) ->
            urlBuilder.parameters.append(key, json.encodeToString(JsonElement.serializer(), value))
        }
        return urlBuilder.build()
    }

    companion object {
        fun isOpenId4VpRequestCandidate(request: String): Boolean = runCatching {
            val parameters = Url(request).parameters
            parameters.contains("dcql_query") ||
                parameters.contains("transaction_data") ||
                parameters["request"]
                    ?.takeIf { it.isJwt() }
                    ?.decodeJws()
                    ?.payload
                    ?.let { payload ->
                        "dcql_query" in payload || "transaction_data" in payload
                    }
                    ?: false
        }.getOrDefault(false)
    }
}
