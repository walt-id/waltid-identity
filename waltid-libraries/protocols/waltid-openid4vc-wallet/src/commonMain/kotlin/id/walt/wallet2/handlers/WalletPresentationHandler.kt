package id.walt.wallet2.handlers

import id.walt.credentials.formats.DigitalCredential
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.DcqlQuery
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.handlers.WalletPresentationHandler.matchCredentials
import id.walt.wallet2.handlers.WalletPresentationHandler.matchCredentialsFromStore
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.serialization.Serializable

private val log = KotlinLogging.logger {}

// ---------------------------------------------------------------------------
// Shared VP request-source contract
// ---------------------------------------------------------------------------

/**
 * Common contract for request types that carry an untrusted OpenID4VP request URL.
 * Resolution and Request Object authentication always happen inside the wallet.
 */
interface VpRequestSource {
    val requestUrl: Url
}

// ---------------------------------------------------------------------------
// Request / response types
// ---------------------------------------------------------------------------

/**
 * Input for the full presentation flow.
 *
 * The request is resolved and authenticated by the wallet before credentials are selected.
 */
@Serializable
data class PresentCredentialRequest(
    /**
     * The OpenID4VP authorization request as a URL.
     * May be an openid4vp:// URL with inline parameters, or an https:// URL
     * whose request_uri parameter points to the actual request object.
     */
    override val requestUrl: Url,

    val keyId: String? = null,
    val did: String? = null,
    val runPolicies: Boolean? = null
) : VpRequestSource

@Serializable
data class PresentCredentialIsolatedRequest(
    override val requestUrl: Url,
    val credentials: List<StoredCredential>,
    val keyId: String? = null,
    val did: String? = null
) : VpRequestSource

// Isolated step types

@Serializable
data class ResolveVpRequestRequest(
    override val requestUrl: Url,
) : VpRequestSource

@Serializable
data class ResolveVpRequestResult(
    /** Complete authenticated request to use for subsequent manual presentation steps. */
    val authorizationRequest: AuthorizationRequest,
    val nonce: String?,
    val clientId: String?,
    val responseUri: Url?,
    val hasRequestUri: Boolean,
    /** The DCQL query from the authorization request, ready to pass to match-credentials or present. */
    val dcqlQuery: DcqlQuery?,
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
        val key = wallet.resolveKey(keyId = request.keyId)
            ?: error("No key available: wallet has no keyStores, no staticKey, and no keyId was specified")
        val did = request.did ?: wallet.defaultDid()
        val keyId = key.getKeyId()
        log.trace { "presentCredential: keyId=$keyId, did=$did, requestUrl=${request.requestUrl}" }

        onEvent(WalletSessionEvent.presentation_request_parsed)

        val result = WalletPresentFunctionality2.walletPresentHandling(
            holderKey = key,
            holderDid = did,
            presentationRequestUrl = request.requestUrl,
            selectCredentialsForQuery = { query ->
                log.trace { "Selecting credentials for DCQL query: ${query.credentials.map { it.id }}" }
                selectFromStores(wallet, query)
                    .also { matched ->
                        log.trace { "DCQL matched queryIds: ${matched.keys}" }
                        onEvent(WalletSessionEvent.presentation_credentials_selected)
                    }
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
        val key = wallet.resolveKey(keyId = request.keyId)
            ?: error("No key available for isolated presentation")
        val did = request.did ?: wallet.defaultDid()

        onEvent(WalletSessionEvent.presentation_request_parsed)

        val rawCredentials = request.credentials.mapIndexed { idx, stored ->
            stored.credential.toRawDcqlCredential(idx.toString())
        }

        val result = WalletPresentFunctionality2.walletPresentHandling(
            holderKey = key,
            holderDid = did,
            presentationRequestUrl = request.requestUrl,
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
        val authRequest = WalletPresentFunctionality2.resolveAuthorizationRequest(request.requestUrl)

        return ResolveVpRequestResult(
            authorizationRequest = authRequest,
            nonce = authRequest.nonce,
            clientId = authRequest.clientId,
            responseUri = authRequest.responseUri?.let { Url(it) },
            hasRequestUri = request.requestUrl.parameters.contains("request_uri"),
            dcqlQuery = authRequest.dcqlQuery,
        )
    }

    /**
     * Runs DCQL matching against the supplied credentials without presenting.
     * Returns which credentials match which query IDs, so the caller can show
     * the user what will be shared before asking for consent.
     */
    suspend fun matchCredentials(request: MatchCredentialsRequest): MatchCredentialsResult {
        // Build index: rawCredential index → wallet credential id
        val idByIndex = request.credentials.withIndex().associate { (idx, stored) -> idx.toString() to stored.id }
        val rawCredentials = request.credentials.mapIndexed { idx, stored ->
            stored.credential.toRawDcqlCredential(idx.toString())
        }
        val matched = DcqlMatcher.match(request.dcqlQuery, rawCredentials).getOrThrow()
        return buildMatchResult(matched, idByIndex)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    // resolveKey is now wallet.resolveKey(keyId = keyId) - see Wallet.resolveKey

    /**
     * Builds a [MatchCredentialsResult] from raw DCQL match results and an index-to-wallet-id map.
     * Extracted to eliminate duplication between [matchCredentials] and [matchCredentialsFromStore].
     */
    private fun buildMatchResult(
        matched: Map<String, List<DcqlMatcher.DcqlMatchResult>>,
        idByIndex: Map<String, String>
    ) = MatchCredentialsResult(
        matchedQueryIds = matched.keys.toList(),
        matchCount = matched.values.sumOf { it.size },
        matchedCredentialIds = matched.mapValues { (_, results) ->
            results.map { idByIndex[it.credential.id] ?: it.credential.id }
        }
    )

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
        // Build idByIndex and rawCredentials in a single streaming pass over the credential stores.
        // selectFromStores uses integer indices as DCQL credential IDs internally; we need the
        // idx -> wallet-assigned-id map to translate them back before returning to the caller.
        val idByIndex = mutableMapOf<String, String>()
        val rawCredentials = mutableListOf<RawDcqlCredential>()
        var idx = 0
        wallet.streamAllCredentials().collect { stored ->
            val key = idx.toString()
            idByIndex[key] = stored.id
            rawCredentials += stored.credential.toRawDcqlCredential(key)
            idx++
        }
        if (rawCredentials.isEmpty()) return MatchCredentialsResult(emptyList(), 0, emptyMap())
        val matched = DcqlMatcher.match(request.dcqlQuery, rawCredentials).getOrThrow()
        return buildMatchResult(matched, idByIndex)
    }

    /**
     * Isolated step 3: build the VP token from the wallet's stored credentials
     * that were selected in step 2.
     *
     * @param wallet The wallet owning the credentials.
     * @param request Contains the resolved authorization request, selected credential IDs,
     *   and optional key/DID overrides.
     */
    suspend fun buildVpToken(wallet: Wallet, request: BuildVpTokenRequest): BuildVpTokenResult {
        val key = wallet.resolveKey(request.key, request.keyId)
            ?: throw IllegalArgumentException("Wallet has no key available for VP token building")
        val did = request.did ?: wallet.defaultDid()

        val dcqlQuery = request.authorizationRequest.dcqlQuery
            ?: throw IllegalArgumentException("AuthorizationRequest has no dcql_query")

        val queriesById = dcqlQuery.credentials.associateBy { it.id }
        val unknownQueryIds = request.selectedCredentialIds.keys - queriesById.keys
        require(unknownQueryIds.isEmpty()) { "Unknown DCQL query IDs selected: $unknownQueryIds" }
        require(request.selectedCredentialIds.isNotEmpty()) { "No credentials selected" }

        val matchedCredentials = request.selectedCredentialIds.mapValues { (queryId, credentialIds) ->
            val query = requireNotNull(queriesById[queryId])
            require(credentialIds.isNotEmpty()) { "No credentials selected for DCQL query '$queryId'" }
            require(query.multiple || credentialIds.size == 1) {
                "DCQL query '$queryId' does not allow multiple credentials"
            }

            val selectedCredentials = credentialIds.distinct().map { credentialId ->
                wallet.findCredential(credentialId)
                    ?: throw IllegalArgumentException("Credential '$credentialId' not found in wallet")
            }
            val matches = DcqlMatcher.match(
                query = DcqlQuery(credentials = listOf(query)),
                availableCredentials = selectedCredentials.map { stored ->
                    stored.credential.toRawDcqlCredential(stored.id)
                },
            ).getOrThrow()[queryId].orEmpty()
            require(matches.size == selectedCredentials.size) {
                "One or more selected credentials do not satisfy DCQL query '$queryId'"
            }
            matches
        }

        val vpToken = WalletPresentFunctionality2.buildVpToken(
            authorizationRequest = request.authorizationRequest,
            matchedCredentials = matchedCredentials,
            holderKey = key,
            holderDid = did,
        )
        val idToken = WalletPresentFunctionality2.buildIdToken(request.authorizationRequest, key, did)
        return BuildVpTokenResult(vpToken = vpToken, idToken = idToken)
    }

    /**
     * Isolated step 4: send the authorization response to the verifier.
     *
     * @param request Contains the authorization request, VP token, and optional ID token.
     * @return The [WalletPresentResult] describing the transmission outcome.
     */
    suspend fun sendAuthorizationResponse(request: SendAuthorizationResponseRequest): WalletPresentResult =
        WalletPresentFunctionality2.sendAuthorizationResponse(
            authorizationRequest = request.authorizationRequest,
            vpToken = request.vpToken,
            idToken = request.idToken,
        ).getOrThrow()

    /**
     * Streams all credentials from all wallet credential stores, converts each
     * to a [RawDcqlCredential], then runs DCQL matching — mirrors the Enterprise
     * WalletPresentFunctionality.selectCredentialsForQuery exactly.
     */
    internal suspend fun selectFromStores(
        wallet: Wallet,
        query: DcqlQuery
    ): Map<String, List<DcqlMatcher.DcqlMatchResult>> {
        if (wallet.credentialStores.isEmpty()) {
            error("Wallet has no credential stores — use presentCredentialIsolated to present inline credentials")
        }

        val rawCredentials = mutableListOf<RawDcqlCredential>()
        var idx = 0
        wallet.streamAllCredentials().collect { stored ->
            log.trace { "  credential[$idx]: id=${stored.id}, format=${stored.credential.format}, issuer=${stored.credential.issuer}" }
            rawCredentials += stored.credential.toRawDcqlCredential(idx.toString())
            idx++
        }

        log.debug { "DCQL matching against $idx stored credential(s), queries=${query.credentials.map { it.id }}" }
        val matched = DcqlMatcher.match(query, rawCredentials).getOrThrow()
        log.trace { "DCQL match result: matchedQueryIds=${matched.keys}, matchCounts=${matched.mapValues { it.value.size }}" }
        return matched
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

// ---------------------------------------------------------------------------
// Isolated-step request / response types for the manual presentation flow
// ---------------------------------------------------------------------------

/**
 * Request to build a VP token from already-matched credentials.
 *
 * Used in the manual presentation flow:
 * 1. `POST /present/resolve-request` - resolves the authorization request
 * 2. `POST /present/match-credentials-from-store` - selects matching credentials
 * 3. `POST /present/build-vp-token` - builds the VP token (this request)
 * 4. `POST /present/send-response` - transmits the response to the verifier
 */
@Serializable
data class BuildVpTokenRequest(
    /** The resolved authorization request from step 1. */
    val authorizationRequest: AuthorizationRequest,
    /**
     * Credential IDs (wallet-assigned) to include, grouped by DCQL query ID.
     * These are the IDs returned by `match-credentials-from-store`.
     */
    val selectedCredentialIds: Map<String, List<String>>,
    /** Key to use for signing. Defaults to the wallet's default key. */
    val key: DirectSerializedKey? = null,
    val keyId: String? = null,
    /** DID to use as holder binding. Defaults to the wallet's default DID. */
    val did: String? = null,
)

@Serializable
data class BuildVpTokenResult(
    /** The serialized `vp_token` JSON string, ready for [SendAuthorizationResponseRequest]. */
    val vpToken: String,
    /** The Self-Issued ID Token for SIOPv2 flows, or null for plain vp_token flows. */
    val idToken: String? = null,
)

/**
 * Request to send the authorization response to the verifier.
 *
 * Final step of the manual presentation flow.
 */
@Serializable
data class SendAuthorizationResponseRequest(
    /** The resolved authorization request from step 1. */
    val authorizationRequest: AuthorizationRequest,
    /** The VP token from step 3. */
    val vpToken: String,
    /** The ID token from step 3, or null. */
    val idToken: String? = null,
)
