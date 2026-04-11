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
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.transactiondata.SUPPORTED_TRANSACTION_DATA_TYPES
import id.walt.verifier.openid.transactiondata.validateRequestTransactionData
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.credentials.CredentialFilterObject
import id.walt.webwallet.service.credentials.CredentialsService
import id.waltid.openid4vp.wallet.request.AuthorizationRequestParameterCodec
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, ExperimentalSerializationApi::class)
class OpenId4VpPresentationService(
    private val http: HttpClient,
    private val credentialService: CredentialsService,
) {
    private val logger = KotlinLogging.logger { }
    private val supportedTransactionDataTypes = SUPPORTED_TRANSACTION_DATA_TYPES
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }

    suspend fun tryResolveAuthorizationRequest(request: String): Result<ResolvedAuthorizationRequest> = runCatching {
        AuthorizationRequestResolver.resolve(request, http).also { resolvedRequest ->
            validateRequestTransactionData(
                transactionData = resolvedRequest.authorizationRequest.transactionData,
                supportedTypes = supportedTransactionDataTypes,
                credentialQueriesById = resolvedRequest.authorizationRequest.dcqlQuery?.credentials?.associateBy { it.id },
            )
        }
    }

    fun buildWalletPresentationRequest(
        request: String,
        resolvedRequest: ResolvedAuthorizationRequest,
    ): Url = when (resolvedRequest) {
        is ResolvedAuthorizationRequest.Plain -> buildWalletPresentationRequest(request, resolvedRequest.authorizationRequest)
        is ResolvedAuthorizationRequest.WithRequestObject -> buildWalletPresentationRequest(request, resolvedRequest.requestObject)
    }

    suspend fun matchCredentialsForPresentationRequest(
        walletId: Uuid,
        request: String,
        selectedCredentialIds: Set<String>? = null,
    ): List<WalletCredential> =
        tryResolveAuthorizationRequest(request)
            .getOrThrow()
            .authorizationRequest
            .dcqlQuery?.let { query ->
                matchCredentials(
                    query = query,
                    credentials = credentialService.list(walletId, CredentialFilterObject.default),
                    selectedCredentialIds = selectedCredentialIds,
                )
            } ?: emptyList()

    suspend fun matchCredentialResults(
        query: DcqlQuery,
        credentials: List<WalletCredential>,
        selectedCredentialIds: Set<String>? = null,
    ): Map<String, List<DcqlMatcher.DcqlMatchResult>> {
        val dcqlCredentials = credentials
            .filter { selectedCredentialIds == null || it.id in selectedCredentialIds }
            .mapNotNull { it.toDcqlCredentialOrNull() }

        return if (dcqlCredentials.isEmpty()) emptyMap()
        else DcqlMatcher.match(query, dcqlCredentials)
            .onFailure { error -> logger.warn(error) { "OpenID4VP credential matching failed" } }
            .getOrThrow()
    }

    internal suspend fun matchCredentials(
        query: DcqlQuery,
        credentials: List<WalletCredential>,
        selectedCredentialIds: Set<String>? = null,
    ): List<WalletCredential> {
        val matchedIds = matchCredentialResults(query, credentials, selectedCredentialIds)
            .values
            .flatten()
            .mapTo(mutableSetOf()) { it.credential.id }

        return credentials.filter { it.id in matchedIds }
    }

    private fun walletPresentationRequestBuilder(request: String): URLBuilder =
        URLBuilder(Url(request).toString().substringBefore("?"))

    private fun buildWalletPresentationRequest(
        request: String,
        resolvedRequest: AuthorizationRequest,
    ): Url = walletPresentationRequestBuilder(request).apply {
        val requestParameters = json
            .encodeToJsonElement(AuthorizationRequest.serializer(), resolvedRequest)
            .jsonObject
            .filterValues { it != JsonNull }

        requestParameters.forEach { (key, value) ->
            parameters.append(key, AuthorizationRequestParameterCodec.encode(json, value))
        }
    }.build()

    private fun buildWalletPresentationRequest(
        request: String,
        requestObject: String,
    ): Url = walletPresentationRequestBuilder(request).apply {
        parameters.append("request", requestObject)
    }.build()

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

    companion object {
        fun isOpenId4VpRequestCandidate(request: String): Boolean = runCatching {
            val parameters = Url(request).parameters
            parameters.contains("dcql_query") ||
                parameters.contains("request_uri") ||
                parameters["request"]
                    ?.takeIf { it.isJwt() }
                    ?.decodeJws()
                    ?.payload
                    ?.let { payload -> "dcql_query" in payload }
                    ?: false
        }.getOrDefault(false)
    }
}
