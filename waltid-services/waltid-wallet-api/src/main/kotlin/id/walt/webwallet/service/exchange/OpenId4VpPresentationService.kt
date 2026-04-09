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
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.credentials.CredentialFilterObject
import id.walt.webwallet.service.credentials.CredentialsService
import id.waltid.openid4vp.wallet.AuthorizationRequestResolver
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }

    suspend fun tryResolveAuthorizationRequest(request: String): Result<AuthorizationRequest> = runCatching {
        AuthorizationRequestResolver.resolve(request, http)
    }

    fun buildWalletPresentationRequest(request: String, resolvedRequest: AuthorizationRequest): Url {
        val requestParameters = json
            .encodeToJsonElement(AuthorizationRequest.serializer(), resolvedRequest)
            .jsonObject
            .filterValues { it != JsonNull }

        return URLBuilder(baseUrlBuilder(Url(request)).build()).apply {
            requestParameters.forEach { (key, value) ->
                parameters.append(key, json.encodeToString(JsonElement.serializer(), value))
            }
        }.build()
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

    internal suspend fun matchCredentials(
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

    companion object {
        fun isOpenId4VpRequestCandidate(request: String): Boolean = runCatching {
            val parameters = Url(request).parameters
            parameters.contains("dcql_query") ||
                parameters.contains("transaction_data") ||
                parameters["request"]
                    ?.takeIf { it.isJwt() }
                    ?.decodeJws()
                    ?.payload
                    ?.let { payload -> "dcql_query" in payload || "transaction_data" in payload }
                    ?: false
        }.getOrDefault(false)
    }
}
