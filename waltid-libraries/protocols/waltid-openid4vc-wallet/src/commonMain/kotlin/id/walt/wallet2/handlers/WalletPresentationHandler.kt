@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.handlers

import id.walt.credentials.formats.DigitalCredential
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.verifier.openid.transactiondata.decodeList
import id.walt.verifier.openid.transactiondata.validateRequestTransactionData
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val log = KotlinLogging.logger {}

// ---------------------------------------------------------------------------
// Request / response types
// ---------------------------------------------------------------------------

/**
 * Input for the full presentation flow.
 *
 * Exactly one of [requestUrl] or [requestObject] must be non-null.
 */
@Serializable
data class PresentCredentialRequest(
    /**
     * The OpenID4VP authorization request as a URL.
     * May be an openid4vp:// URL with inline parameters, or an https:// URL
     * whose request_uri parameter points to the actual request object.
     */
    val requestUrl: Url? = null,

    /**
     * The OpenID4VP authorization request as an already-fetched JSON object.
     * Use this when the request has been resolved out-of-band.
     */
    val requestObject: JsonObject? = null,

    val keyId: String? = null,
    val did: String? = null,
    val runPolicies: Boolean? = null
) {
    init {
        check(requestUrl != null || requestObject != null) {
            "Either requestUrl or requestObject must be provided"
        }
        check(requestUrl == null || requestObject == null) {
            "Only one of requestUrl or requestObject may be provided, not both"
        }
    }

    fun getEffectiveRequestUrl(): String =
        requestUrl?.toString() ?: requestObject?.toString() ?: error("No request source available")
}

@Serializable
data class PresentCredentialIsolatedRequest(
    val requestUrl: Url? = null,
    val requestObject: JsonObject? = null,
    val credentials: List<StoredCredential>,
    val keyId: String? = null,
    val did: String? = null
) {
    init {
        check(requestUrl != null || requestObject != null) {
            "Either requestUrl or requestObject must be provided"
        }
        check(requestUrl == null || requestObject == null) {
            "Only one of requestUrl or requestObject may be provided, not both"
        }
    }

    fun getEffectiveRequestUrl(): String =
        requestUrl?.toString() ?: requestObject?.toString() ?: error("No request source available")
}

// Isolated step types

@Serializable
data class ResolveVpRequestRequest(
    val requestUrl: Url? = null,
    val requestObject: JsonObject? = null
) {
    init {
        check(requestUrl != null || requestObject != null) {
            "Either requestUrl or requestObject must be provided"
        }
    }

    fun getEffectiveRequestUrl(): String =
        requestUrl?.toString() ?: requestObject?.toString() ?: error("No request source available")
}

@Serializable
data class ResolveVpRequestResult(
    val nonce: String?,
    val clientId: String?,
    val responseUri: Url?,
    val hasRequestUri: Boolean
)

@Serializable
data class MatchCredentialsRequest(
    val dcqlQuery: DcqlQuery,
    val credentials: List<StoredCredential>
)

/**
 * Request for matching a DCQL query against the wallet's own credential stores.
 * No credentials need to be supplied inline — they are loaded from the wallet.
 */
@Serializable
data class MatchCredentialsFromStoreRequest(
    val dcqlQuery: DcqlQuery
)

@Serializable
data class MatchCredentialsResult(
    /** DCQL query IDs for which at least one credential matched. */
    val matchedQueryIds: List<String>,
    /** Total number of credential matches across all query IDs. */
    val matchCount: Int,
    /** For each matched query ID, the wallet-assigned IDs of matching credentials. */
    val matchedCredentialIds: Map<String, List<String>>
)

@Serializable
data class PreviewPresentationRequest(
    val requestUrl: Url
)

data class PreviewPresentationResult(
    val authorizationRequest: AuthorizationRequest,
    val credentialOptions: List<PresentationCredentialOption>,
    val credentialRequirements: List<PresentationCredentialRequirement>,
    val transactionData: List<PresentationTransactionDataItem>,
)

data class PresentationCredentialRequirement(
    val options: List<List<String>>,
)

data class PresentationCredentialOption(
    val queryId: String,
    val credentialId: String,
    val multiple: Boolean,
    val format: String,
    val issuer: String?,
    val subject: String?,
    val label: String?,
    val credentialData: JsonObject,
    val disclosures: List<PresentationDisclosure>,
)

data class PresentationDisclosure(
    val path: String,
    val name: String?,
    val value: JsonElement,
    val selectivelyDisclosable: Boolean,
    val required: Boolean,
    val selectable: Boolean,
)

data class PresentationTransactionDataItem(
    val type: String,
    val credentialQueryIds: List<String>,
    val rawJson: JsonObject,
    val details: JsonObject,
)

@Serializable
data class PresentationCredentialSelection(
    val queryId: String,
    val credentialId: String,
)

@Serializable
data class PresentationDisclosureSelection(
    val queryId: String,
    val credentialId: String,
    val path: String,
)

@Serializable
data class SubmitPresentationRequest(
    val requestUrl: Url,
    val selectedCredentialOptions: List<PresentationCredentialSelection>,
    val selectedDisclosureOptions: List<PresentationDisclosureSelection>? = null,
    val did: String? = null,
    val runPolicies: Boolean? = null,
)

class MissingPresentationPreviewException :
    IllegalStateException("Presentation request preview expired or was not found; preview the request again before submitting.")

// ---------------------------------------------------------------------------
// Handler
// ---------------------------------------------------------------------------

/**
 * OpenID4VP 1.0 credential presentation logic.
 *
 * Delegates to [WalletPresentFunctionality2] from waltid-openid4vp-wallet,
 * exactly as the Enterprise wallet does. The wallet-specific work here is:
 * - resolving the holder key and DID from the [Wallet]
 * - providing the selectCredentialsForQuery lambda that streams from [Wallet.credentialStores]
 * - providing holder policies (currently none; extendable)
 *
 * Works exclusively with DCQL (OpenID4VP 1.0). Presentation Exchange is not
 * supported here by design.
 */
object WalletPresentationHandler {
    private const val MAX_PREVIEWED_AUTHORIZATION_REQUESTS = 16
    private val authorizationRequestJson = Json { ignoreUnknownKeys = true }
    private val previewedAuthorizationRequests = LinkedHashMap<PresentationPreviewCacheKey, ResolvedAuthorizationRequest>()
    private val previewedAuthorizationRequestsMutex = Mutex()

    /**
     * Full presentation flow: resolve VP request → DCQL-match credentials
     * from wallet stores → sign → submit to verifier's response_uri.
     */
    suspend fun presentCredential(
        wallet: Wallet,
        request: PresentCredentialRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
    ): WalletPresentResult {
        val key = resolveKey(wallet, request.keyId)
            ?: error("No key available: wallet has no keyStores, no staticKey, and no keyId was specified")
        val did = request.did ?: wallet.defaultDid()
        val keyId = key.getKeyId()
        log.trace { "presentCredential: keyId=$keyId, did=$did, requestUrl=${request.requestUrl}" }

        onEvent(WalletSessionEvent.presentation_request_parsed)

        val result = WalletPresentFunctionality2.walletPresentHandling(
            holderKey = key,
            holderDid = did,
            presentationRequestUrl = Url(request.getEffectiveRequestUrl()),
            selectCredentialsForQuery = { query ->
                log.trace { "Selecting credentials for DCQL query: ${query.credentials.map { it.id }}" }
                selectFromStores(wallet, query)
                    .also { matched ->
                        log.trace { "DCQL matched queryIds: ${matched.keys}" }
                        onEvent(WalletSessionEvent.presentation_credentials_selected)
                    }
            },
            holderPoliciesToRun = null,
            runPolicies = request.runPolicies,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
        )

        if (result.isSuccess) {
            onEvent(WalletSessionEvent.presentation_completed)
            log.info { "Presentation completed successfully" }
        } else {
            onEvent(WalletSessionEvent.presentation_failed)
            log.warn { "Presentation failed: ${result.exceptionOrNull()?.message}" }
        }

        return result.getOrThrow()
    }

    /**
     * Isolated (stateless) presentation: caller supplies credentials inline.
     */
    suspend fun presentCredentialIsolated(
        wallet: Wallet,
        request: PresentCredentialIsolatedRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
    ): WalletPresentResult {
        val key = resolveKey(wallet, request.keyId)
            ?: error("No key available for isolated presentation")
        val did = request.did ?: wallet.defaultDid()

        onEvent(WalletSessionEvent.presentation_request_parsed)

        val rawCredentials = request.credentials.mapIndexed { idx, stored ->
            stored.credential.toRawDcqlCredential(idx.toString())
        }

        val result = WalletPresentFunctionality2.walletPresentHandling(
            holderKey = key,
            holderDid = did,
            presentationRequestUrl = Url(request.getEffectiveRequestUrl()),
            selectCredentialsForQuery = { query ->
                DcqlMatcher.match(query, rawCredentials).getOrThrow()
                    .also { onEvent(WalletSessionEvent.presentation_credentials_selected) }
            },
            holderPoliciesToRun = null,
            runPolicies = null,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
        )

        if (result.isSuccess) {
            onEvent(WalletSessionEvent.presentation_completed)
        } else {
            onEvent(WalletSessionEvent.presentation_failed)
        }

        return result.getOrThrow()
    }

    suspend fun previewPresentation(
        wallet: Wallet,
        request: PreviewPresentationRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
    ): PreviewPresentationResult {
        onEvent(WalletSessionEvent.presentation_request_parsed)
        val resolvedAuthorizationRequest = resolveAuthorizationRequest(request.requestUrl)
        val authorizationRequest = resolvedAuthorizationRequest.authorizationRequest
        val query = authorizationRequest.dcqlQuery ?: error("Missing dcql_query for AuthorizationRequest")
        val transactionDataItems = validateRequestTransactionData(
            transactionData = authorizationRequest.transactionData,
            typeRegistry = transactionDataTypeRegistry,
            credentialQueriesById = query.credentials.associateBy { it.id },
        )
        val transactionData = transactionDataItems.map { decoded ->
            PresentationTransactionDataItem(
                type = decoded.transactionData.type,
                credentialQueryIds = decoded.transactionData.credentialIds,
                rawJson = decoded.rawJson,
                details = decoded.details,
            )
        }
        rememberPreviewedAuthorizationRequest(wallet, request.requestUrl, resolvedAuthorizationRequest)
        val storedById = wallet.streamAllCredentials().toList().associateBy { it.id }
        val matched = selectFromStores(wallet, query, useWalletCredentialIds = true)
        onEvent(WalletSessionEvent.presentation_credentials_selected)

        return PreviewPresentationResult(
            authorizationRequest = authorizationRequest,
            credentialRequirements = query.requiredCredentialRequirements(),
            credentialOptions = matched.flatMap { (queryId, results) ->
                results.map { result ->
                    val raw = result.credential as RawDcqlCredential
                    val stored = storedById[raw.id] ?: error("Credential '${raw.id}' disappeared while building presentation preview")
                    val credential = stored.credential
                    PresentationCredentialOption(
                        queryId = queryId,
                        credentialId = stored.id,
                        multiple = result.originalQuery.multiple,
                        format = credential.format,
                        issuer = credential.issuer,
                        subject = credential.subject,
                        label = stored.label,
                        credentialData = credential.credentialData,
                        disclosures = result.toPresentationDisclosures(),
                    )
                }
            },
            transactionData = transactionData,
        )
    }

    suspend fun submitPresentation(
        wallet: Wallet,
        request: SubmitPresentationRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
    ): WalletPresentResult {
        request.selectedCredentialOptions.requireValidPresentationCredentialSelection()
        val resolvedAuthorizationRequest = consumePreviewedAuthorizationRequest(wallet, request.requestUrl)
        val key = resolveKey(wallet, null)
            ?: error("No key available: wallet has no keyStores, no staticKey, and no keyId was specified")
        val did = request.did ?: wallet.defaultDid()
        val selectedQueryIds = request.selectedCredentialOptions.mapTo(mutableSetOf()) { it.queryId }
        validateSelectedTransactionDataCredentials(
            resolvedAuthorizationRequest.authorizationRequest.transactionData.orEmpty(),
            selectedQueryIds,
        )

        onEvent(WalletSessionEvent.presentation_request_parsed)

        val result = WalletPresentFunctionality2.walletPresentHandling(
            holderKey = key,
            holderDid = did,
            presentationRequestUrl = request.requestUrl,
            resolvedAuthorizationRequest = resolvedAuthorizationRequest,
            selectCredentialsForQuery = { query ->
                val requirements = query.requiredCredentialRequirements()
                require(requirements.satisfiedBy(selectedQueryIds)) {
                    "Selected credential option(s) do not satisfy required presentation credential query constraints"
                }

                val matched = selectFromStores(
                    wallet = wallet,
                    query = query,
                    useWalletCredentialIds = true,
                )
                val selected = matched.selectCredentialOptions(
                    selectedCredentialOptions = request.selectedCredentialOptions,
                    selectedDisclosureOptions = request.selectedDisclosureOptions,
                )
                require(requirements.satisfiedBy(selected.keys)) {
                    "Selected credential option(s) do not match required presentation credential query constraints"
                }

                selected.also {
                    onEvent(WalletSessionEvent.presentation_credentials_selected)
                }
            },
            holderPoliciesToRun = null,
            runPolicies = request.runPolicies,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
        )

        if (result.isSuccess) {
            onEvent(WalletSessionEvent.presentation_completed)
        } else {
            onEvent(WalletSessionEvent.presentation_failed)
        }

        return result.getOrThrow()
    }
    // ---------------------------------------------------------------------------
    // Isolated step handlers
    // ---------------------------------------------------------------------------

    /**
     * Lightweight resolver for the legacy isolated server endpoint.
     *
     * This returns only request metadata for callers that still drive the older
     * resolve -> match -> present flow. New presentation flows should use
     * [previewPresentation], which performs full request-object resolution and
     * verifier validation through [AuthorizationRequestResolver].
     */
    suspend fun resolveRequest(request: ResolveVpRequestRequest): ResolveVpRequestResult {
        val url = Url(request.getEffectiveRequestUrl())
        val fetcher = WebDataFetcher(WebDataFetcherId.OPENID4VP_WALLET_RESOLVE_AUTHORIZATIONREQUEST)

        val authRequest: AuthorizationRequest = if (url.parameters.contains("request_uri")) {
            val requestUri = url.parameters["request_uri"]!!
            log.debug { "Fetching Request Object from request_uri: $requestUri" }
            val httpResponse = fetcher.rawFetch(requestUri)

            if (!httpResponse.status.value.let { it in 200..299 }) {
                error("Failed to fetch Request Object from $requestUri: ${httpResponse.status}")
            }

            val bodyText = httpResponse.bodyAsText()
            if (bodyText.trimStart().startsWith("{")) {
                // Plain JSON request object
                authorizationRequestJson.decodeFromString<AuthorizationRequest>(bodyText)
            } else {
                // Signed JWT — decode payload only (signature verification is handled by walletPresentHandling)
                val jwtParts = bodyText.trim().split(".")
                if (jwtParts.size >= 2) {
                    val paddedPayload = jwtParts[1].let { it + "=".repeat((4 - it.length % 4) % 4) }
                    val payload = paddedPayload.decodeFromBase64Url().decodeToString()
                    authorizationRequestJson.decodeFromString<AuthorizationRequest>(payload)
                } else {
                    error("Unexpected Request Object format from $requestUri")
                }
            }
        } else {
            // Parse inline URL parameters
            val params = url.parameters.entries().associate { (k, vs) -> k to vs.firstOrNull().orEmpty() }
            val jsonObj = buildJsonObject { params.forEach { (k, v) -> put(k, v) } }
            authorizationRequestJson.decodeFromJsonElement<AuthorizationRequest>(jsonObj)
        }

        return ResolveVpRequestResult(
            nonce = authRequest.nonce,
            clientId = authRequest.clientId,
            responseUri = authRequest.responseUri?.let { Url(it) },
            hasRequestUri = url.parameters.contains("request_uri")
        )
    }

    /**
     * Runs DCQL matching against the supplied credentials without presenting.
     * Returns which credentials match which query IDs, so the caller can show
     * the user what will be shared before asking for consent.
     */
    suspend fun matchCredentials(request: MatchCredentialsRequest): MatchCredentialsResult {
        // Build index: rawCredential index → wallet credential id
        val idByIndex = request.credentials.mapIndexed { idx, stored -> idx.toString() to stored.id }.toMap()

        val rawCredentials = request.credentials.mapIndexed { idx, stored ->
            stored.credential.toRawDcqlCredential(idx.toString())
        }
        val matched = DcqlMatcher.match(request.dcqlQuery, rawCredentials).getOrThrow()

        val matchedCredentialIds = matched.mapValues { (_, results) ->
            results.map { result -> idByIndex[result.credential.id] ?: result.credential.id }
        }

        return MatchCredentialsResult(
            matchedQueryIds = matched.keys.toList(),
            matchCount = matched.values.sumOf { it.size },
            matchedCredentialIds = matchedCredentialIds
        )
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private suspend fun resolveKey(wallet: Wallet, keyId: String?) = when {
        keyId != null -> wallet.findKey(keyId)
            ?: error("Key '$keyId' not found in any wallet key store")
        else -> wallet.defaultKey()
    }

    /**
     * DCQL-matches the wallet's own stored credentials against [query] without
     * presenting anything. Used to preview what credentials and fields would be
     * shared before asking the user for consent.
     *
     * Unlike [matchCredentials], the caller does not need to supply credentials
     * inline — they are streamed from [Wallet.credentialStores].
     */
    suspend fun matchCredentialsFromStore(
        wallet: Wallet,
        request: MatchCredentialsFromStoreRequest
    ): MatchCredentialsResult {
        val matched = selectFromStores(wallet, request.dcqlQuery)
        val matchedCredentialIds = matched.mapValues { (_, results) ->
            results.map { result -> result.credential.id }
        }
        return MatchCredentialsResult(
            matchedQueryIds = matched.keys.toList(),
            matchCount = matched.values.sumOf { it.size },
            matchedCredentialIds = matchedCredentialIds
        )
    }

    /**
     * Streams all credentials from all wallet credential stores, converts each
     * to a [RawDcqlCredential], then runs DCQL matching — mirrors the Enterprise
     * WalletPresentFunctionality.selectCredentialsForQuery exactly.
     */
    internal suspend fun selectFromStores(
        wallet: Wallet,
        query: DcqlQuery,
        useWalletCredentialIds: Boolean = false,
    ): Map<String, List<DcqlMatcher.DcqlMatchResult>> {
        if (wallet.credentialStores.isEmpty()) {
            error("Wallet has no credential stores — use presentCredentialIsolated to present inline credentials")
        }

        val rawCredentials = mutableListOf<RawDcqlCredential>()
        var idx = 0
        wallet.streamAllCredentials().collect { stored ->
            log.trace { "  credential[$idx]: id=${stored.id}, format=${stored.credential.format}, issuer=${stored.credential.issuer}" }
            rawCredentials += stored.credential.toRawDcqlCredential(
                id = if (useWalletCredentialIds) stored.id else idx.toString(),
            )
            idx++
        }

        log.debug { "DCQL matching against $idx stored credential(s), queries=${query.credentials.map { it.id }}" }
        val matched = DcqlMatcher.match(query, rawCredentials).getOrThrow()
        log.trace { "DCQL match result: matchedQueryIds=${matched.keys}, matchCounts=${matched.mapValues { it.value.size }}" }
        return matched
    }

    internal fun DcqlQuery.requiredCredentialRequirements(): List<PresentationCredentialRequirement> =
        credentialSets
            ?.takeIf { it.isNotEmpty() }
            ?.let { sets ->
                sets.filter { it.required }
                    .map { PresentationCredentialRequirement(options = it.options) }
            }
            ?: listOf(PresentationCredentialRequirement(options = listOf(credentials.map { it.id })))

    internal fun List<PresentationCredentialRequirement>.satisfiedBy(selectedQueryIds: Set<String>): Boolean =
        all { requirement ->
            requirement.options.any { option ->
                option.isNotEmpty() && option.all { queryId -> queryId in selectedQueryIds }
            }
        }

    internal fun List<PresentationCredentialSelection>.requireValidPresentationCredentialSelection() {
        require(isNotEmpty()) {
            "At least one credential option must be selected for presentation"
        }
        require(all { it.queryId.isNotBlank() && it.credentialId.isNotBlank() }) {
            "Selected presentation credential options must include non-blank query and credential IDs"
        }
        val duplicateSelection = groupingBy { it }
            .eachCount()
            .entries
            .firstOrNull { (_, count) -> count > 1 }
            ?.key
        require(duplicateSelection == null) {
            "Selected presentation credential options must not contain duplicate query and credential IDs"
        }
    }

    internal fun List<PresentationDisclosureSelection>.requireValidPresentationDisclosureSelection() {
        require(all { it.queryId.isNotBlank() && it.credentialId.isNotBlank() && it.path.isNotBlank() }) {
            "Selected presentation disclosure options must include non-blank query IDs, credential IDs, and paths"
        }
    }

    internal fun Map<String, List<DcqlMatcher.DcqlMatchResult>>.selectCredentialOptions(
        selectedCredentialOptions: List<PresentationCredentialSelection>,
        selectedDisclosureOptions: List<PresentationDisclosureSelection>? = null,
    ): Map<String, List<DcqlMatcher.DcqlMatchResult>> {
        selectedCredentialOptions.requireValidPresentationCredentialSelection()
        selectedDisclosureOptions?.requireValidPresentationDisclosureSelection()
        val selectedOptions = selectedCredentialOptions.toSet()
        val availableOptions = flatMap { (queryId, results) ->
            results.map { result -> result.toPresentationCredentialSelection(queryId) }
        }.toSet()

        val unknownSelection = selectedOptions.firstOrNull { selection -> selection !in availableOptions }
        require(unknownSelection == null) {
            "Selected credential option does not match the presentation preview"
        }

        val multipleAllowedByQueryId = mapValues { (_, results) ->
            results.firstOrNull()?.originalQuery?.multiple == true
        }
        val invalidMultipleSelection = selectedOptions
            .groupBy { selection -> selection.queryId }
            .entries
            .firstOrNull { (queryId, selections) ->
                selections.size > 1 && multipleAllowedByQueryId[queryId] != true
            }
        require(invalidMultipleSelection == null) {
            "Selected credential options must not contain multiple credentials for a non-multiple presentation query"
        }

        val selectedDisclosurePathsByOption = selectedDisclosureOptions
            ?.groupBy(
                keySelector = { selection ->
                    PresentationCredentialSelection(
                        queryId = selection.queryId,
                        credentialId = selection.credentialId,
                    )
                },
                valueTransform = { selection -> selection.path },
            )
            ?.mapValues { (_, paths) -> paths.toSet() }
        val unselectedDisclosureOption = selectedDisclosurePathsByOption
            ?.keys
            ?.firstOrNull { selection -> selection !in selectedOptions }
        require(unselectedDisclosureOption == null) {
            "Selected disclosure option does not match a selected credential option"
        }

        return mapValues { (queryId, results) ->
            results.filter { result ->
                result.toPresentationCredentialSelection(queryId) in selectedOptions
            }.map { result ->
                if (selectedDisclosurePathsByOption == null) {
                    result
                } else {
                    val option = result.toPresentationCredentialSelection(queryId)
                    result.selectDisclosures(
                        selectedPaths = selectedDisclosurePathsByOption[option].orEmpty(),
                    )
                }
            }
        }.filterValues { it.isNotEmpty() }
            .also { selected ->
                require(selected.isNotEmpty()) {
                    "At least one selected credential option must match the presentation request"
                }
            }
    }

    private fun DcqlMatcher.DcqlMatchResult.selectDisclosures(
        selectedPaths: Set<String>,
    ): DcqlMatcher.DcqlMatchResult {
        val plan = originalQuery.claimSelectionPlan()
        val disclosures = availablePresentationDisclosures(plan) ?: run {
            require(selectedPaths.isEmpty()) {
                "Selected disclosure option does not match the presentation preview"
            }
            return this
        }
        val selectivelyDisclosablePaths = disclosures
            .filterValues { value -> value is DcqlDisclosure }
            .keys
        val unknownPaths = selectedPaths - selectivelyDisclosablePaths
        require(unknownPaths.isEmpty()) {
            "Selected disclosure option does not match a selectively disclosable presentation claim"
        }
        val retainedPaths = plan.requiredPaths + selectedPaths
        val retainedClaimPaths = retainedPaths + disclosures
            .filterValues { value -> value !is DcqlDisclosure }
            .keys
        require(plan.satisfiedBy(retainedClaimPaths)) {
            "Selected disclosure option(s) do not satisfy required presentation claim constraints"
        }

        return copy(
            selectedDisclosures = disclosures.filter { (path, value) ->
                value !is DcqlDisclosure || path in retainedPaths
            }
        )
    }

    private fun DcqlMatcher.DcqlMatchResult.toPresentationCredentialSelection(queryId: String) =
        PresentationCredentialSelection(
            queryId = queryId,
            credentialId = credential.id,
        )

    private data class PresentationPreviewCacheKey(
        val walletId: String,
        val requestUrl: String,
    )

    private suspend fun resolveAuthorizationRequest(requestUrl: Url): ResolvedAuthorizationRequest {
        val fetcher = WebDataFetcher(WebDataFetcherId.OPENID4VP_WALLET_RESOLVE_AUTHORIZATIONREQUEST)
        return AuthorizationRequestResolver.resolve(
            requestUrl = requestUrl,
            unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
            fetchRequestUri = { requestUri, requestUriMethod ->
                AuthorizationRequestResolver.fetchRequestUriWithWebDataFetcher(
                    webResolveAuthReq = fetcher,
                    requestUri = requestUri,
                    requestUriMethod = requestUriMethod,
                )
            },
        )
    }

    private suspend fun rememberPreviewedAuthorizationRequest(
        wallet: Wallet,
        requestUrl: Url,
        resolvedAuthorizationRequest: ResolvedAuthorizationRequest,
    ) = previewedAuthorizationRequestsMutex.withLock {
        if (previewedAuthorizationRequests.size >= MAX_PREVIEWED_AUTHORIZATION_REQUESTS) {
            previewedAuthorizationRequests.remove(previewedAuthorizationRequests.keys.first())
        }
        previewedAuthorizationRequests[presentationPreviewCacheKey(wallet, requestUrl)] = resolvedAuthorizationRequest
    }

    private suspend fun consumePreviewedAuthorizationRequest(
        wallet: Wallet,
        requestUrl: Url,
    ): ResolvedAuthorizationRequest =
        previewedAuthorizationRequestsMutex.withLock {
            previewedAuthorizationRequests.remove(presentationPreviewCacheKey(wallet, requestUrl))
        } ?: throw MissingPresentationPreviewException()

    private fun presentationPreviewCacheKey(wallet: Wallet, requestUrl: Url): PresentationPreviewCacheKey =
        PresentationPreviewCacheKey(walletId = wallet.id, requestUrl = requestUrl.toString())

    internal fun DcqlMatcher.DcqlMatchResult.toPresentationDisclosures(): List<PresentationDisclosure> {
        val plan = originalQuery.claimSelectionPlan()
        return availablePresentationDisclosures(plan).orEmpty().map { (path, value) ->
            val required = plan.isRequired(path)
            val selectable = plan.isSelectable(path, value)
            when (value) {
                is DcqlDisclosure -> PresentationDisclosure(
                    path = path,
                    name = value.name,
                    value = value.value,
                    selectivelyDisclosable = true,
                    required = required,
                    selectable = selectable,
                )
                is JsonElement -> PresentationDisclosure(
                    path = path,
                    name = path.substringAfterLast('.', path),
                    value = value,
                    selectivelyDisclosable = false,
                    required = required,
                    selectable = false,
                )
                else -> PresentationDisclosure(
                    path = path,
                    name = path.substringAfterLast('.', path),
                    value = JsonPrimitive(value.toString()),
                    selectivelyDisclosable = false,
                    required = required,
                    selectable = false,
                )
            }
        }
    }

    private fun DcqlMatcher.DcqlMatchResult.availablePresentationDisclosures(
        plan: ClaimSelectionPlan,
    ): Map<String, Any>? {
        val selected = selectedDisclosures ?: return null
        if (plan.optionalPaths.isEmpty()) return selected

        val expanded = linkedMapOf<String, Any>()
        val selectedByPath = selected.toMutableMap()
        originalQuery.claims.orEmpty().forEach { claim ->
            val path = claim.pathKey()
            val selectedValue = selectedByPath.remove(path)
            when {
                selectedValue != null -> expanded[path] = selectedValue
                path in plan.optionalPaths -> findMatchingDisclosure(claim)?.let { expanded[path] = it }
            }
        }
        selectedByPath.forEach { (path, value) -> expanded[path] = value }
        return expanded
    }

    private fun DcqlMatcher.DcqlMatchResult.findMatchingDisclosure(claim: ClaimsQuery): DcqlDisclosure? {
        val claimName = claim.path
            .lastOrNull { pathPart -> pathPart is JsonPrimitive && pathPart.isString }
            ?.jsonPrimitive?.content
            ?: return null
        val allowedValues = claim.values.orEmpty()

        return credential.disclosures?.firstOrNull { disclosure ->
            disclosure.name == claimName && (allowedValues.isEmpty() || disclosure.value in allowedValues)
        }
    }

    private data class ClaimSelectionPlan(
        val requiredPaths: Set<String>,
        val optionalPaths: Set<String>,
        private val allClaimPaths: Set<String>,
        private val claimSetOptions: List<Set<String>>?,
    ) {
        fun isRequired(path: String): Boolean = path in requiredPaths

        fun isSelectable(path: String, value: Any): Boolean = path in optionalPaths && value is DcqlDisclosure

        fun satisfiedBy(selectedPathKeys: Set<String>): Boolean =
            claimSetOptions
                ?.any { option -> option.isNotEmpty() && option.all { path -> path in selectedPathKeys } }
                ?: allClaimPaths.all { path -> path in selectedPathKeys }
    }

    private fun CredentialQuery.claimSelectionPlan(): ClaimSelectionPlan {
        val claims = claims.orEmpty()
        val allClaimPaths = claims.mapTo(linkedSetOf()) { it.pathKey() }
        val claimSets = claimSets
        if (claimSets.isNullOrEmpty()) {
            return ClaimSelectionPlan(
                requiredPaths = allClaimPaths,
                optionalPaths = emptySet(),
                allClaimPaths = allClaimPaths,
                claimSetOptions = null,
            )
        }

        val pathByClaimId = claims
            .mapNotNull { claim -> claim.id?.let { id -> id to claim.pathKey() } }
            .toMap()
        val requiredClaimIds = claimSets
            .map { ids -> ids.toSet() }
            .reduceOrNull { required, option -> required intersect option }
            .orEmpty()
        val claimIdsInAnySet = claimSets.flatten().toSet()

        return ClaimSelectionPlan(
            requiredPaths = requiredClaimIds.mapNotNullTo(linkedSetOf()) { id -> pathByClaimId[id] },
            optionalPaths = (claimIdsInAnySet - requiredClaimIds).mapNotNullTo(linkedSetOf()) { id -> pathByClaimId[id] },
            allClaimPaths = allClaimPaths,
            claimSetOptions = claimSets.mapNotNull { optionIds ->
                optionIds
                    .mapNotNullTo(linkedSetOf()) { id -> pathByClaimId[id] }
                    .takeIf { paths -> paths.size == optionIds.size }
            },
        )
    }

    private fun ClaimsQuery.pathKey(): String = path.joinToString(".")

    internal fun validateSelectedTransactionDataCredentials(
        transactionData: List<String>,
        selectedQueryIds: Set<String>,
    ) {
        decodeList(transactionData).forEach { decoded ->
            val selectedTransactionCredentialIds = decoded.transactionData.credentialIds
                .filter { it in selectedQueryIds }
            require(selectedTransactionCredentialIds.size == 1) {
                "transaction_data credential_ids must reference exactly one selected credential for transaction authorization"
            }
        }
    }

    private fun DigitalCredential.toRawDcqlCredential(id: String): RawDcqlCredential {
        val sdvc = this as? id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
        return RawDcqlCredential(
            id = id,
            format = format,
            data = credentialData,
            originalCredential = this,
            disclosures = sdvc?.disclosures?.map { DcqlDisclosure(it.name, it.value) }
        )
    }
}
