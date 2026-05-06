package id.walt.wallet2.handlers

import id.walt.credentials.formats.DigitalCredential
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.DcqlQuery
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
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

@Serializable
data class MatchCredentialsResult(
    /** DCQL query IDs for which at least one credential matched. */
    val matchedQueryIds: List<String>,
    /** Total number of credential matches across all query IDs. */
    val matchCount: Int,
    /** For each matched query ID, the wallet-assigned IDs of matching credentials. */
    val matchedCredentialIds: Map<String, List<String>>
)

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

    /**
     * Full presentation flow: resolve VP request → DCQL-match credentials
     * from wallet stores → sign → submit to verifier's response_uri.
     */
    suspend fun presentCredential(
        wallet: Wallet,
        request: PresentCredentialRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {}
    ): WalletPresentResult {
        val key = resolveKey(wallet, request.keyId)
            ?: error("No key available: wallet has no keyStores, no staticKey, and no keyId was specified")
        val did = request.did ?: wallet.defaultDid()

        onEvent(WalletSessionEvent.presentation_request_parsed)

        val result = WalletPresentFunctionality2.walletPresentHandling(
            holderKey = key,
            holderDid = did,
            presentationRequestUrl = Url(request.getEffectiveRequestUrl()),
            selectCredentialsForQuery = { query ->
                selectFromStores(wallet, query)
                    .also { onEvent(WalletSessionEvent.presentation_credentials_selected) }
            },
            holderPoliciesToRun = null,
            runPolicies = request.runPolicies
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
        onEvent: suspend (WalletSessionEvent) -> Unit = {}
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
            runPolicies = null
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
     * Resolves the VP authorization request from a URL or JSON object.
     *
     * Handles:
     * - Inline parameters in the URL (openid4vp:// with encoded params)
     * - request_uri: fetches the actual Request Object from the URI, then
     *   decodes it (supports both signed JWT and plain JSON content types)
     *
     * This mirrors the request resolution logic inside
     * [WalletPresentFunctionality2.walletPresentHandling].
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
                Json { ignoreUnknownKeys = true }.decodeFromString<AuthorizationRequest>(bodyText)
            } else {
                // Signed JWT — decode payload only (signature verification is handled by walletPresentHandling)
                val jwtParts = bodyText.trim().split(".")
                if (jwtParts.size >= 2) {
                    val paddedPayload = jwtParts[1].let { it + "=".repeat((4 - it.length % 4) % 4) }
                    val payload = paddedPayload.decodeFromBase64Url().decodeToString()
                    Json { ignoreUnknownKeys = true }.decodeFromString<AuthorizationRequest>(payload)
                } else {
                    error("Unexpected Request Object format from $requestUri")
                }
            }
        } else {
            // Parse inline URL parameters
            val params = url.parameters.entries().associate { (k, vs) -> k to vs.firstOrNull().orEmpty() }
            val jsonObj = buildJsonObject { params.forEach { (k, v) -> put(k, v) } }
            Json { ignoreUnknownKeys = true }.decodeFromJsonElement<AuthorizationRequest>(jsonObj)
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
     * Streams all credentials from all wallet credential stores, converts each
     * to a [RawDcqlCredential], then runs DCQL matching — mirrors the Enterprise
     * WalletPresentFunctionality.selectCredentialsForQuery exactly.
     */
    private suspend fun selectFromStores(
        wallet: Wallet,
        query: DcqlQuery
    ): Map<String, List<DcqlMatcher.DcqlMatchResult>> {
        if (wallet.credentialStores.isEmpty()) {
            error("Wallet has no credential stores — use presentCredentialIsolated to present inline credentials")
        }

        val rawCredentials = mutableListOf<RawDcqlCredential>()
        var idx = 0
        wallet.streamAllCredentials().collect { stored ->
            rawCredentials += stored.credential.toRawDcqlCredential(idx.toString())
            idx++
        }

        log.debug { "DCQL matching against $idx stored credential(s)" }
        return DcqlMatcher.match(query, rawCredentials).getOrThrow()
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
