package id.walt.wallet2.handlers

import id.walt.credentials.formats.DigitalCredential
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.DcqlQuery
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult
import id.waltid.openid4vp.wallet.response.ResponseEncryptionHandler
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
    val hasRequestUri: Boolean,
    /** Response mode from the authorization request (e.g., direct_post, direct_post.jwt). */
    val responseMode: String? = null,
    /** True if the verifier requires encrypted responses (direct_post.jwt or dc_api.jwt). */
    val requiresEncryptedResponse: Boolean = false
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

/**
 * Result of inspecting encryption requirements for a VP request.
 *
 * Per OID4VP 1.0 §6, encrypted responses are required when response_mode
 * is `direct_post.jwt` or `dc_api.jwt`. This result allows mobile UIs to
 * show encryption status in consent screens before presenting.
 */
@Serializable
data class EncryptionRequirementsResult(
    /** True if the verifier requires encrypted responses. */
    val isEncryptionRequired: Boolean,
    /** Content encryption algorithm that will be used (e.g., "A128GCM"). */
    val encAlgorithm: String?,
    /** Key agreement algorithm (always "ECDH-ES" per spec). */
    val algAlgorithm: String?,
    /** Thumbprint of verifier's encryption key for display/audit purposes. */
    val verifierKeyThumbprint: String?
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
            hasRequestUri = url.parameters.contains("request_uri"),
            responseMode = authRequest.responseMode?.name,
            requiresEncryptedResponse = authRequest.responseMode in OpenID4VPResponseMode.ENCRYPTED_RESPONSES
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

    /**
     * Inspects a resolved authorization request to determine encryption requirements.
     *
     * Per OID4VP 1.0 §6, when response_mode is `direct_post.jwt` or `dc_api.jwt`,
     * the wallet must encrypt the authorization response. This method extracts
     * and validates the encryption parameters from the request's client_metadata.
     *
     * Use this for consent screens to show users whether their response will be encrypted.
     *
     * @param authorizationRequest The resolved authorization request to inspect.
     * @return Encryption requirements including algorithm details and verifier key info.
     */
    suspend fun inspectEncryptionRequirements(
        authorizationRequest: AuthorizationRequest
    ): EncryptionRequirementsResult {
        val responseMode = authorizationRequest.responseMode
        
        // Check if encryption is required based on response_mode
        val requiresEncryption = responseMode in OpenID4VPResponseMode.ENCRYPTED_RESPONSES
        
        if (!requiresEncryption) {
            return EncryptionRequirementsResult(
                isEncryptionRequired = false,
                encAlgorithm = null,
                algAlgorithm = null,
                verifierKeyThumbprint = null
            )
        }
        
        // Extract encryption config from client_metadata
        val encryptionConfig = ResponseEncryptionHandler.extractEncryptionConfig(authorizationRequest)
            .getOrElse { error ->
                log.warn { "Failed to extract encryption config: ${error.message}" }
                return EncryptionRequirementsResult(
                    isEncryptionRequired = true,
                    encAlgorithm = null,
                    algAlgorithm = null,
                    verifierKeyThumbprint = null
                )
            }
        
        if (encryptionConfig == null) {
            return EncryptionRequirementsResult(
                isEncryptionRequired = true,
                encAlgorithm = null,
                algAlgorithm = null,
                verifierKeyThumbprint = null
            )
        }
        
        // Get verifier key thumbprint for audit/display
        val thumbprint = runCatching {
            encryptionConfig.verifierKey.getThumbprint()
        }.getOrNull()
        
        return EncryptionRequirementsResult(
            isEncryptionRequired = true,
            encAlgorithm = encryptionConfig.encAlgorithm,
            algAlgorithm = encryptionConfig.algAlgorithm,
            verifierKeyThumbprint = thumbprint
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
