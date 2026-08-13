@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.handlers

import id.walt.credentials.formats.DigitalCredential
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto2.keys.KeyUsage
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.verifier.openid.transactiondata.decodeList
import id.walt.verifier.openid.transactiondata.validateRequestTransactionData
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletKeyStoreEntry
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.data.resolveKeyMaterial
import id.walt.wallet2.handlers.WalletPresentationHandler.matchCredentials
import id.walt.wallet2.handlers.WalletPresentationHandler.matchCredentialsFromStore
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import id.waltid.openid4vp.wallet.PresentationRequestError
import id.waltid.openid4vp.wallet.PresentationRequestValidationResult
import id.waltid.openid4vp.wallet.PresentationRequestValidator
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult
import id.waltid.openid4vp.wallet.WalletPresentationFormatRegistry
import id.waltid.openid4vp.wallet.DcApiCredentialResponse
import id.waltid.openid4vp.wallet.DcApiWallet
import id.waltid.openid4vp.wallet.ResolvedDcApiRequest
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import id.waltid.openid4vp.wallet.response.ResponseEncryption
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.jvm.JvmInline

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

    /** Inline signing key; takes precedence over [keyId]. */
    val key: DirectSerializedKey? = null,
    val keyId: String? = null,
    val did: String? = null,
    val runPolicies: Boolean? = null
) : VpRequestSource

internal fun WalletPresentResult.presentationOutcomeEvent(): WalletSessionEvent = when {
    transmissionSuccess == true -> WalletSessionEvent.presentation_completed
    transmissionSuccess == false -> WalletSessionEvent.presentation_failed
    getUrl != null || formPostHtml != null -> WalletSessionEvent.presentation_response_prepared
    else -> WalletSessionEvent.presentation_failed
}

private suspend fun Result<WalletPresentResult>.emitPresentationOutcome(
    onEvent: suspend (WalletSessionEvent) -> Unit,
): WalletPresentResult {
    exceptionOrNull()?.let { error ->
        onEvent(WalletSessionEvent.presentation_failed)
        throw error
    }
    return getOrThrow().also { result -> onEvent(result.presentationOutcomeEvent()) }
}

@Serializable
data class PresentCredentialIsolatedRequest(
    override val requestUrl: Url,
    val credentials: List<StoredCredential>,
    /** Inline signing key; takes precedence over [keyId]. */
    val key: DirectSerializedKey? = null,
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

/**
 * Request for a consent preview of an OpenID4VP authorization request.
 *
 * [key] (inline) takes precedence over [keyId] for wallet capability advertisement and format
 * validation during preview. Defaults to the wallet's default signing key.
 */
@Serializable
data class PreviewPresentationRequest(
    val requestUrl: Url,
    /** Inline signing key; takes precedence over [keyId]. */
    val key: DirectSerializedKey? = null,
    val keyId: String? = null,
)

/** Opaque identifier binding a presentation action to one reviewed request resolution. */
@Serializable
@JvmInline
value class PresentationPreviewHandle(val value: String) {
    init {
        require(value.isNotBlank()) { "Presentation preview handle must not be blank" }
    }

    override fun toString(): String = "PresentationPreviewHandle(<redacted>)"
}

sealed interface PreviewPresentationResult {
    val handle: PresentationPreviewHandle

    data class Ready(
        override val handle: PresentationPreviewHandle,
        val authorizationRequest: AuthorizationRequest,
        /** Response-encryption selection derived from this authenticated request, or `null` for a plain response. */
        val responseEncryption: ResponseEncryption.Metadata?,
        val credentialOptions: List<PresentationCredentialOption>,
        val credentialRequirements: List<PresentationCredentialRequirement>,
        val transactionData: List<PresentationTransactionDataItem>,
    ) : PreviewPresentationResult

    data class Invalid(
        override val handle: PresentationPreviewHandle,
        val authorizationRequest: AuthorizationRequest,
        val error: PresentationRequestError,
    ) : PreviewPresentationResult
}

/**
 * Stateless consent-preview result for HTTP APIs that must not retain a server-side preview session.
 *
 * Same UI payload as [PreviewPresentationResult], but without a [PresentationPreviewHandle].
 * [authorizationRequest] is returned for display / technical details only — callers must **not**
 * echo it into [buildVpToken] or [sendAuthorizationResponse]. Complete the flow by passing the
 * original `requestUrl` to those steps (or [rejectPresentationByRequestUrl]).
 */
sealed interface StatelessPreviewPresentationResult {
    val authorizationRequest: AuthorizationRequest

    data class Ready(
        override val authorizationRequest: AuthorizationRequest,
        val keyId: String,
        val responseEncryption: ResponseEncryption.Metadata?,
        val credentialOptions: List<PresentationCredentialOption>,
        val credentialRequirements: List<PresentationCredentialRequirement>,
        val transactionData: List<PresentationTransactionDataItem>,
    ) : StatelessPreviewPresentationResult

    data class Invalid(
        override val authorizationRequest: AuthorizationRequest,
        val error: PresentationRequestError,
    ) : StatelessPreviewPresentationResult
}

data class PreviewDcApiPresentationRequest(
    val protocol: String,
    val data: JsonObject,
    val origin: String,
    val eligibleCredentialIds: Set<String>? = null,
)

data class PreviewDcApiPresentationResult(
    val requestId: String,
    val resolvedRequest: ResolvedDcApiRequest,
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
    val previewHandle: PresentationPreviewHandle,
    val selectedCredentialOptions: List<PresentationCredentialSelection>,
    val selectedDisclosureOptions: List<PresentationDisclosureSelection>? = null,
    val keyId: String? = null,
    val did: String? = null,
    val runPolicies: Boolean? = null,
)

@Serializable
data class RejectPresentationRequest(
    val previewHandle: PresentationPreviewHandle,
    val errorCode: String? = null,
    val errorDescription: String? = null,
)

/** Reject a presentation by re-resolving the original request URL (no preview handle / session). */
@Serializable
data class RejectPresentationByRequestUrlRequest(
    val requestUrl: Url,
    val errorCode: String? = null,
    val errorDescription: String? = null,
)

data class SubmitDcApiPresentationRequest(
    val requestId: String,
    val selectedCredentialOptions: List<PresentationCredentialSelection>,
    val selectedDisclosureOptions: List<PresentationDisclosureSelection>? = null,
    val did: String? = null,
)

class MissingPresentationPreviewException :
    IllegalStateException("Presentation request preview expired or was not found; preview the request again before responding.")

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
    internal sealed interface PreviewedPresentation {
        val requestUrl: Url
        val resolvedAuthorizationRequest: ResolvedAuthorizationRequest

        data class Ready(
            override val requestUrl: Url,
            override val resolvedAuthorizationRequest: ResolvedAuthorizationRequest,
            /**
             * The concrete signing key selected while previewing. Submission re-resolves exactly this
             * key so that the request was validated against the key that actually signs the response.
             */
            val keyId: String,
        ) : PreviewedPresentation

        data class Invalid(
            override val requestUrl: Url,
            override val resolvedAuthorizationRequest: ResolvedAuthorizationRequest,
            val error: PresentationRequestError,
        ) : PreviewedPresentation
    }

    private val previewedAuthorizationRequests =
        PreviewSessionStore<PreviewedPresentation>(sessionName = "Presentation")
    private val previewedDcApiRequests =
        PreviewSessionStore<PreviewedDcApiRequest>(sessionName = "Digital Credentials presentation")

    private data class PreviewedDcApiRequest(
        val request: ResolvedDcApiRequest,
        val allowedCredentialIds: Set<String>,
    )

    /**
     * Full presentation flow: resolve VP request → DCQL-match credentials
     * from wallet stores → sign → submit to verifier's response_uri.
     */
    suspend fun presentCredential(
        wallet: Wallet,
        request: PresentCredentialRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
    ): WalletPresentResult = presentCredential(
        wallet, request, onEvent, TransactionDataTypeRegistry(), beforeCredentialsUsed = {},
    )

    suspend fun presentCredential(
        wallet: Wallet,
        request: PresentCredentialRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit,
        beforeCredentialsUsed: suspend (Int) -> Unit,
    ): WalletPresentResult = presentCredential(
        wallet, request, onEvent, TransactionDataTypeRegistry(), beforeCredentialsUsed,
    )

    suspend fun presentCredential(
        wallet: Wallet,
        request: PresentCredentialRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit,
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
    ): WalletPresentResult = presentCredentialWithTrust(
        wallet,
        request,
        onEvent,
        transactionDataTypeRegistry,
        ClientIdTrustConfiguration(),
    )

    suspend fun presentCredential(
        wallet: Wallet,
        request: PresentCredentialRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit,
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
        beforeCredentialsUsed: suspend (Int) -> Unit,
    ): WalletPresentResult = presentCredentialWithTrust(
        wallet,
        request,
        onEvent,
        transactionDataTypeRegistry,
        ClientIdTrustConfiguration(),
        beforeCredentialsUsed,
    )

    suspend fun presentCredentialWithTrust(
        wallet: Wallet,
        request: PresentCredentialRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
        clientIdTrustConfiguration: ClientIdTrustConfiguration,
        beforeCredentialsUsed: suspend (Int) -> Unit = {},
    ): WalletPresentResult {
        val keyMaterial = request.key?.key?.let { WalletKeyStoreEntry(it.getKeyId(), it, null) }
            ?: wallet.resolveKeyMaterial(request.keyId, setOf(KeyUsage.SIGN))
            ?: error("No key available: wallet has no keyStores, no staticKey, and no keyId was specified")
        val did = request.did ?: wallet.defaultDid()
        log.trace { "presentCredential: keyId=${keyMaterial.keyId}, did=$did, requestUrl=${request.requestUrl}" }

        onEvent(WalletSessionEvent.presentation_request_parsed)

        val result = presentWithKeyMaterial(
            keyMaterial = keyMaterial,
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
            runPolicies = request.runPolicies,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
            clientIdTrustConfiguration = clientIdTrustConfiguration,
            beforeCredentialsUsed = beforeCredentialsUsed,
        )

        return result.emitPresentationOutcome(onEvent)
    }

    /**
     * Isolated (stateless) presentation: caller supplies credentials inline.
     */
    suspend fun presentCredentialIsolated(
        wallet: Wallet,
        request: PresentCredentialIsolatedRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
    ): WalletPresentResult = presentCredentialIsolated(
        wallet, request, onEvent, TransactionDataTypeRegistry(), beforeCredentialsUsed = {},
    )

    suspend fun presentCredentialIsolated(
        wallet: Wallet,
        request: PresentCredentialIsolatedRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit,
        beforeCredentialsUsed: suspend (Int) -> Unit,
    ): WalletPresentResult = presentCredentialIsolated(
        wallet, request, onEvent, TransactionDataTypeRegistry(), beforeCredentialsUsed,
    )

    suspend fun presentCredentialIsolated(
        wallet: Wallet,
        request: PresentCredentialIsolatedRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit,
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
    ): WalletPresentResult = presentCredentialIsolatedWithTrust(
        wallet,
        request,
        onEvent,
        transactionDataTypeRegistry,
        ClientIdTrustConfiguration(),
    )

    suspend fun presentCredentialIsolated(
        wallet: Wallet,
        request: PresentCredentialIsolatedRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit,
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
        beforeCredentialsUsed: suspend (Int) -> Unit,
    ): WalletPresentResult = presentCredentialIsolatedWithTrust(
        wallet,
        request,
        onEvent,
        transactionDataTypeRegistry,
        ClientIdTrustConfiguration(),
        beforeCredentialsUsed,
    )

    suspend fun presentCredentialIsolatedWithTrust(
        wallet: Wallet,
        request: PresentCredentialIsolatedRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
        clientIdTrustConfiguration: ClientIdTrustConfiguration,
        beforeCredentialsUsed: suspend (Int) -> Unit = {},
    ): WalletPresentResult {
        val keyMaterial = request.key?.key?.let { WalletKeyStoreEntry(it.getKeyId(), it, null) }
            ?: wallet.resolveKeyMaterial(request.keyId, setOf(KeyUsage.SIGN))
            ?: error("No key available for isolated presentation")
        val did = request.did ?: wallet.defaultDid()

        onEvent(WalletSessionEvent.presentation_request_parsed)

        val rawCredentials = request.credentials.mapIndexed { idx, stored ->
            stored.credential.toRawDcqlCredential(idx.toString())
        }

        val result = presentWithKeyMaterial(
            keyMaterial = keyMaterial,
            holderDid = did,
            presentationRequestUrl = request.requestUrl,
            selectCredentialsForQuery = { query ->
                DcqlMatcher.match(query, rawCredentials).getOrThrow()
                    .also { onEvent(WalletSessionEvent.presentation_credentials_selected) }
            },
            runPolicies = null,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
            clientIdTrustConfiguration = clientIdTrustConfiguration,
            beforeCredentialsUsed = beforeCredentialsUsed,
        )

        return result.emitPresentationOutcome(onEvent)
    }

    suspend fun previewPresentation(
        wallet: Wallet,
        request: PreviewPresentationRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
    ): PreviewPresentationResult = previewPresentationWithTrust(
        wallet,
        request,
        onEvent,
        transactionDataTypeRegistry,
        ClientIdTrustConfiguration(),
    )

    suspend fun previewPresentationWithTrust(
        wallet: Wallet,
        request: PreviewPresentationRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
        clientIdTrustConfiguration: ClientIdTrustConfiguration,
    ): PreviewPresentationResult {
        // Selected once up front so advertised wallet metadata, request validation and the retained
        // preview all refer to the same key, but only *required* where a key is genuinely needed: a
        // request that fails resolution or client-ID trust validation must report that failure rather
        // than a wallet-local missing-key condition.
        val executionKey = resolvePreviewKeyMaterial(wallet, request).requiredOnUse()
        return previewPresentation(
            wallet = wallet,
            request = request,
            executionKey = executionKey,
            onEvent = onEvent,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
            resolveAuthorizationRequest = { requestUrl ->
                resolveAuthorizationRequest(
                    { executionKey().presentationCapabilities() },
                    requestUrl,
                    clientIdTrustConfiguration,
                )
            },
        )
    }

    /**
     * Stateless consent preview for HTTP APIs: same resolution/validation/matching as
     * [previewPresentation], but does not retain a preview handle or server-side session.
     *
     * Uses [PreviewPresentationRequest.keyId] (or the wallet default) for capability advertisement
     * and format validation. The resolved key id is returned on [StatelessPreviewPresentationResult.Ready]
     * and must be passed to [buildVpToken] as [BuildVpTokenRequest.keyId].
     */
    suspend fun previewPresentationStateless(
        wallet: Wallet,
        request: PreviewPresentationRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
        clientIdTrustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
        resolveAuthorizationRequest: (suspend (Url) -> ResolvedAuthorizationRequest)? = null,
    ): StatelessPreviewPresentationResult {
        val keyMaterial = resolvePreviewKeyMaterial(wallet, request).requiredOnUse()
        val resolveRequest = resolveAuthorizationRequest ?: { requestUrl ->
            this@WalletPresentationHandler.resolveAuthorizationRequest(
                { keyMaterial().presentationCapabilities() },
                requestUrl,
                clientIdTrustConfiguration,
            )
        }
        onEvent(WalletSessionEvent.presentation_request_parsed)
        val resolvedAuthorizationRequest = resolveRequest(request.requestUrl)
        val authorizationRequest = resolvedAuthorizationRequest.authorizationRequest
        val validation = PresentationRequestValidator.validate(
            resolvedRequest = resolvedAuthorizationRequest,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
            formatCapabilities = { keyMaterial().presentationCapabilities() },
        )
        if (validation is PresentationRequestValidationResult.Invalid) {
            return StatelessPreviewPresentationResult.Invalid(
                authorizationRequest = authorizationRequest,
                error = validation.error,
            )
        }
        val valid = validation as PresentationRequestValidationResult.Valid
        val query = requireNotNull(authorizationRequest.dcqlQuery)
        val transactionData = valid.transactionData.map { decoded ->
            PresentationTransactionDataItem(
                type = decoded.transactionData.type,
                credentialQueryIds = decoded.transactionData.credentialIds,
                rawJson = decoded.rawJson,
                details = decoded.details,
            )
        }
        val responseEncryption = ResponseEncryption.resolveCrypto2(authorizationRequest)?.metadata()
        val storedById = wallet.streamAllCredentials().toList().associateBy { it.id }
        val matched = selectFromStores(wallet, query, useWalletCredentialIds = true)
        val availableCredentialQueryIds = matched.filterValues { it.isNotEmpty() }.keys
        val availabilityError = PresentationRequestValidator.validateTransactionDataCredentialAvailability(
            transactionData = valid.transactionData,
            availableCredentialQueryIds = availableCredentialQueryIds,
        ) ?: PresentationRequestValidator.validateCredentialAvailability(
            query = query,
            availableCredentialQueryIds = availableCredentialQueryIds,
        )
        if (availabilityError != null) {
            PresentationRequestValidator.requireErrorResponseCanBeSent(resolvedAuthorizationRequest)
            return StatelessPreviewPresentationResult.Invalid(
                authorizationRequest = authorizationRequest,
                error = availabilityError,
            )
        }
        onEvent(WalletSessionEvent.presentation_credentials_selected)
        val credentialOptions = matched.flatMap { (queryId, results) ->
            results.map { result ->
                val raw = result.credential as RawDcqlCredential
                val stored = storedById[raw.id]
                    ?: error("Credential '${raw.id}' disappeared while building presentation preview")
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
        }
        return StatelessPreviewPresentationResult.Ready(
            authorizationRequest = authorizationRequest,
            keyId = keyMaterial().keyId,
            responseEncryption = responseEncryption,
            credentialOptions = credentialOptions,
            credentialRequirements = query.requiredCredentialRequirements(),
            transactionData = transactionData,
        )
    }

    internal suspend fun previewPresentation(
        wallet: Wallet,
        request: PreviewPresentationRequest,
        executionKey: () -> WalletKeyStoreEntry,
        onEvent: suspend (WalletSessionEvent) -> Unit,
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
        resolveAuthorizationRequest: suspend (Url) -> ResolvedAuthorizationRequest,
    ): PreviewPresentationResult {
        onEvent(WalletSessionEvent.presentation_request_parsed)
        val resolvedAuthorizationRequest = resolveAuthorizationRequest(request.requestUrl)
        val authorizationRequest = resolvedAuthorizationRequest.authorizationRequest
        val validation = PresentationRequestValidator.validate(
            resolvedRequest = resolvedAuthorizationRequest,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
            formatCapabilities = { executionKey().presentationCapabilities() },
        )
        if (validation is PresentationRequestValidationResult.Invalid) {
            val handle = rememberPreviewedAuthorizationRequest(
                wallet = wallet,
                preview = PreviewedPresentation.Invalid(
                    requestUrl = request.requestUrl,
                    resolvedAuthorizationRequest = resolvedAuthorizationRequest,
                    error = validation.error,
                ),
            )
            return PreviewPresentationResult.Invalid(handle, authorizationRequest, validation.error)
        }

        val valid = validation as PresentationRequestValidationResult.Valid
        val query = requireNotNull(authorizationRequest.dcqlQuery)
        val transactionData = valid.transactionData.map { decoded ->
            PresentationTransactionDataItem(
                type = decoded.transactionData.type,
                credentialQueryIds = decoded.transactionData.credentialIds,
                rawJson = decoded.rawJson,
                details = decoded.details,
            )
        }
        val responseEncryption = ResponseEncryption.resolveCrypto2(authorizationRequest)?.metadata()
        val storedById = wallet.streamAllCredentials().toList().associateBy { it.id }
        val matched = selectFromStores(wallet, query, useWalletCredentialIds = true)
        val availableCredentialQueryIds = matched.filterValues { it.isNotEmpty() }.keys
        val availabilityError = PresentationRequestValidator.validateTransactionDataCredentialAvailability(
            transactionData = valid.transactionData,
            availableCredentialQueryIds = availableCredentialQueryIds,
        ) ?: PresentationRequestValidator.validateCredentialAvailability(
            query = query,
            availableCredentialQueryIds = availableCredentialQueryIds,
        )
        if (availabilityError != null) {
            PresentationRequestValidator.requireErrorResponseCanBeSent(resolvedAuthorizationRequest)
            val handle = rememberPreviewedAuthorizationRequest(
                wallet = wallet,
                preview = PreviewedPresentation.Invalid(
                    requestUrl = request.requestUrl,
                    resolvedAuthorizationRequest = resolvedAuthorizationRequest,
                    error = availabilityError,
                ),
            )
            return PreviewPresentationResult.Invalid(handle, authorizationRequest, availabilityError)
        }
        onEvent(WalletSessionEvent.presentation_credentials_selected)
        val credentialOptions = matched.flatMap { (queryId, results) ->
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
        }
        val handle = rememberPreviewedAuthorizationRequest(
            wallet = wallet,
            preview = PreviewedPresentation.Ready(
                requestUrl = request.requestUrl,
                resolvedAuthorizationRequest = resolvedAuthorizationRequest,
                keyId = executionKey().keyId,
            ),
        )
        return PreviewPresentationResult.Ready(
            handle = handle,
            authorizationRequest = authorizationRequest,
            responseEncryption = responseEncryption,
            credentialRequirements = query.requiredCredentialRequirements(),
            credentialOptions = credentialOptions,
            transactionData = transactionData,
        )
    }

    suspend fun previewDcApiPresentation(
        wallet: Wallet,
        request: PreviewDcApiPresentationRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
    ): PreviewDcApiPresentationResult {
        onEvent(WalletSessionEvent.presentation_request_parsed)
        val resolvedRequest = DcApiWallet.resolveRequest(
            protocol = request.protocol,
            data = request.data,
            origin = request.origin,
        )
        val authorizationRequest = resolvedRequest.authorizationRequest
        val query = requireNotNull(authorizationRequest.dcqlQuery)
        val transactionDataItems = validateRequestTransactionData(
            transactionData = authorizationRequest.transactionData,
            typeRegistry = transactionDataTypeRegistry,
            credentialQueriesById = query.credentials.associateBy { it.id },
        )
        // response_mode=dc_api.jwt is unanswerable without usable verifier encryption metadata, and
        // the mdoc session transcript is thumbprinted from the same key. Resolving it here rather
        // than at response-build time means an unusable configuration is rejected before any
        // credential is read, so a verifier cannot get a consent dialog - and the disclosure of what
        // the wallet holds that comes with it - for a request it could never have received an answer
        // to. The value is not retained: submission re-resolves it from the same immutable retained
        // Authorization Request, so there is nothing to keep in sync.
        if (authorizationRequest.responseMode == OpenID4VPResponseMode.DC_API_JWT) {
            val encryption = requireNotNull(ResponseEncryption.resolveCrypto2(authorizationRequest)) {
                "response_mode=dc_api.jwt requires client_metadata response-encryption keys"
            }
            // Thumbprinting is what canonicalizes the published coordinates, and it is the same value
            // the mdoc session transcript binds to, so taking it here rejects key material that is
            // well-formed enough to be selected but cannot actually be encrypted to.
            encryption.thumbprint()
        }
        val storedById = wallet.streamAllCredentials().toList().associateBy { it.id }
        val matched = selectFromStores(wallet, query, useWalletCredentialIds = true)
        onEvent(WalletSessionEvent.presentation_credentials_selected)
        val credentialOptions = matched.flatMap { (queryId, results) ->
            results.map { result ->
                val raw = result.credential as RawDcqlCredential
                val stored = storedById[raw.id]
                    ?: error("Credential '${raw.id}' disappeared while building DC API presentation preview")
                PresentationCredentialOption(
                    queryId = queryId,
                    credentialId = stored.id,
                    multiple = result.originalQuery.multiple,
                    format = stored.credential.format,
                    issuer = stored.credential.issuer,
                    subject = stored.credential.subject,
                    label = stored.label,
                    credentialData = stored.credential.credentialData,
                    disclosures = result.toPresentationDisclosures(),
                )
            }
        }.filter { option ->
            request.eligibleCredentialIds?.let { option.credentialId in it } ?: true
        }
        val credentialRequirements = query.requiredCredentialRequirements()
        val offeredQueryIds = credentialOptions.mapTo(mutableSetOf()) { it.queryId }
        require(credentialOptions.isNotEmpty() && credentialRequirements.satisfiedBy(offeredQueryIds)) {
            "The selected registry entries no longer satisfy this request"
        }
        val requestId = rememberPreviewedDcApiRequest(
            wallet = wallet,
            request = resolvedRequest,
            allowedCredentialIds = credentialOptions.mapTo(mutableSetOf()) { it.credentialId },
        )

        return PreviewDcApiPresentationResult(
            requestId = requestId,
            resolvedRequest = resolvedRequest,
            credentialRequirements = credentialRequirements,
            credentialOptions = credentialOptions,
            transactionData = transactionDataItems.map { decoded ->
                PresentationTransactionDataItem(
                    type = decoded.transactionData.type,
                    credentialQueryIds = decoded.transactionData.credentialIds,
                    rawJson = decoded.rawJson,
                    details = decoded.details,
                )
            },
        )
    }

    suspend fun submitPresentation(
        wallet: Wallet,
        request: SubmitPresentationRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
    ): WalletPresentResult {
        request.selectedCredentialOptions.requireValidPresentationCredentialSelection()
        val preview = consumePreviewedAuthorizationRequest(wallet, request.previewHandle) { cached ->
            require(cached is PreviewedPresentation.Ready) {
                "Cannot submit an invalid presentation request; reject it or dismiss it locally"
            }
        }
        val ready = preview as? PreviewedPresentation.Ready
            ?: error("Unexpected presentation preview state")
        val resolvedAuthorizationRequest = ready.resolvedAuthorizationRequest
        // Sign with exactly the key the request was validated against during preview.
        val keyMaterial = wallet.resolveKeyMaterial(ready.keyId, setOf(KeyUsage.SIGN))
            ?: error("Key '${ready.keyId}' selected while previewing is no longer available")
        val did = request.did ?: wallet.defaultDid()
        val selectedQueryIds = request.selectedCredentialOptions.mapTo(mutableSetOf()) { it.queryId }
        validateSelectedTransactionDataCredentials(
            resolvedAuthorizationRequest.authorizationRequest.transactionData.orEmpty(),
            selectedQueryIds,
        )

        onEvent(WalletSessionEvent.presentation_request_parsed)

        val result = presentWithKeyMaterial(
            keyMaterial = keyMaterial,
            holderDid = did,
            presentationRequestUrl = preview.requestUrl,
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
            runPolicies = request.runPolicies,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
        )

        return result.emitPresentationOutcome(onEvent)
    }

    /**
     * Rejects exactly one reviewed presentation and atomically consumes its preview handle.
     */
    suspend fun rejectPresentation(
        wallet: Wallet,
        request: RejectPresentationRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
    ): WalletPresentResult {
        val preview = consumePreviewedAuthorizationRequest(wallet, request.previewHandle) { cached ->
            val detectedCode = (cached as? PreviewedPresentation.Invalid)?.error?.code?.code
            require(detectedCode == null || request.errorCode == null || request.errorCode == detectedCode) {
                "The error code for an invalid presentation request is determined by the wallet"
            }
        }
        val authorizationRequest = preview.resolvedAuthorizationRequest.authorizationRequest
        val detectedError = (preview as? PreviewedPresentation.Invalid)?.error
        val errorCode = detectedError?.code?.code ?: request.errorCode
            ?: WalletPresentFunctionality2.OID4VPErrorCode.ACCESS_DENIED.code
        PresentationRequestValidator.requireErrorResponseCanBeSent(preview.resolvedAuthorizationRequest)
        onEvent(WalletSessionEvent.presentation_request_parsed)

        val result = WalletPresentFunctionality2.walletRejectHandling(
            authorizationRequest = authorizationRequest,
            error = errorCode,
            errorDescription = request.errorDescription.takeIf { detectedError == null },
        )

        return result.emitPresentationOutcome(onEvent)
    }

    /**
     * Rejects a presentation by re-resolving [RejectPresentationByRequestUrlRequest.requestUrl].
     * No preview handle or server-side session is required.
     */
    suspend fun rejectPresentationByRequestUrl(
        request: RejectPresentationByRequestUrlRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        clientIdTrustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
    ): WalletPresentResult {
        val resolvedAuthorizationRequest = resolveAuthorizationRequest(
            { WalletPresentationFormatRegistry.defaultCapabilities() },
            request.requestUrl,
            clientIdTrustConfiguration,
        )
        PresentationRequestValidator.requireErrorResponseCanBeSent(resolvedAuthorizationRequest)
        onEvent(WalletSessionEvent.presentation_request_parsed)
        val errorCode = request.errorCode
            ?: WalletPresentFunctionality2.OID4VPErrorCode.ACCESS_DENIED.code
        val result = WalletPresentFunctionality2.walletRejectHandling(
            authorizationRequest = resolvedAuthorizationRequest.authorizationRequest,
            error = errorCode,
            errorDescription = request.errorDescription,
        )
        return result.emitPresentationOutcome(onEvent)
    }

    private suspend fun presentWithKeyMaterial(
        keyMaterial: WalletKeyStoreEntry,
        holderDid: String?,
        presentationRequestUrl: Url,
        selectCredentialsForQuery: suspend (DcqlQuery) -> Map<String, List<DcqlMatcher.DcqlMatchResult>>,
        runPolicies: Boolean?,
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
        resolvedAuthorizationRequest: ResolvedAuthorizationRequest? = null,
        clientIdTrustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
        beforeCredentialsUsed: suspend (Int) -> Unit = {},
    ): Result<WalletPresentResult> = keyMaterial.crypto2Key?.let { crypto2Key ->
        WalletPresentFunctionality2.walletPresentHandling(
            holderKey = crypto2Key,
            holderDid = holderDid,
            presentationRequestUrl = presentationRequestUrl,
            selectCredentialsForQuery = selectCredentialsForQuery,
            holderPoliciesToRun = null,
            runPolicies = runPolicies,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
            resolvedAuthorizationRequest = resolvedAuthorizationRequest,
            clientIdTrustConfiguration = clientIdTrustConfiguration,
            beforeCredentialsUsed = beforeCredentialsUsed,
        )
    } ?: WalletPresentFunctionality2.walletPresentHandling(
        holderKey = requireNotNull(keyMaterial.legacyKey) {
            "Key '${keyMaterial.keyId}' has no usable signing representation"
        },
        holderDid = holderDid,
        presentationRequestUrl = presentationRequestUrl,
        selectCredentialsForQuery = selectCredentialsForQuery,
        holderPoliciesToRun = null,
        runPolicies = runPolicies,
        transactionDataTypeRegistry = transactionDataTypeRegistry,
        resolvedAuthorizationRequest = resolvedAuthorizationRequest,
        holderCrypto2Key = null,
        clientIdTrustConfiguration = clientIdTrustConfiguration,
        beforeCredentialsUsed = beforeCredentialsUsed,
    )

    suspend fun submitDcApiPresentation(
        wallet: Wallet,
        request: SubmitDcApiPresentationRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
    ): DcApiCredentialResponse {
        request.selectedCredentialOptions.requireValidPresentationCredentialSelection()
        return previewedDcApiRequests.useRetainingOnFailure(wallet.id, request.requestId) { previewedRequest ->
            require(request.selectedCredentialOptions.all { it.credentialId in previewedRequest.allowedCredentialIds }) {
                "A selected credential was not offered by the retained Digital Credentials preview"
            }
            val resolvedRequest = previewedRequest.request
            val authorizationRequest = resolvedRequest.authorizationRequest
            // The Digital Credentials API is a crypto2-only surface: it ships no v1 key path, so a
            // wallet whose key material has no crypto2 representation cannot present through it.
            val keyMaterial = wallet.resolveKeyMaterial(null, setOf(KeyUsage.SIGN))
                ?: error("No key available: wallet has no keyStores and no staticKey")
            val holderKey = requireNotNull(keyMaterial.crypto2Key) {
                "Key '${keyMaterial.keyId}' has no crypto2 signing representation"
            }
            val did = request.did ?: wallet.defaultDid()
            val selectedQueryIds = request.selectedCredentialOptions.mapTo(mutableSetOf()) { it.queryId }
            validateSelectedTransactionDataCredentials(
                authorizationRequest.transactionData.orEmpty(),
                selectedQueryIds,
            )
            onEvent(WalletSessionEvent.presentation_request_parsed)

            val selectCredentialsForQuery: suspend (DcqlQuery) -> Map<String, List<DcqlMatcher.DcqlMatchResult>> =
                { query ->
                    val requirements = query.requiredCredentialRequirements()
                    require(requirements.satisfiedBy(selectedQueryIds)) {
                        "Selected credential option(s) do not satisfy required DC API credential query constraints"
                    }
                    val selected = selectFromStores(
                        wallet = wallet,
                        query = query,
                        useWalletCredentialIds = true,
                    ).selectCredentialOptions(
                        selectedCredentialOptions = request.selectedCredentialOptions,
                        selectedDisclosureOptions = request.selectedDisclosureOptions,
                    )
                    require(requirements.satisfiedBy(selected.keys)) {
                        "Selected credential option(s) do not match required DC API credential query constraints"
                    }
                    selected.also { onEvent(WalletSessionEvent.presentation_credentials_selected) }
                }

            WalletPresentFunctionality2.walletPresentDcApiHandling(
                holderKey = holderKey,
                holderDid = did,
                request = resolvedRequest,
                selectCredentialsForQuery = selectCredentialsForQuery,
                transactionDataTypeRegistry = transactionDataTypeRegistry,
            ).getOrElse { error ->
                onEvent(WalletSessionEvent.presentation_failed)
                throw error
            }.also {
                onEvent(WalletSessionEvent.presentation_completed)
            }
        }
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
     * Builds a VP token after re-resolving and revalidating [BuildVpTokenRequest.requestUrl].
     *
     * Sensitive request fields (DCQL, nonce, transaction data) come from the freshly
     * resolved [AuthorizationRequest], never from a client-echoed copy.
     *
     * After a [previewPresentationStateless] Ready result, pass that result's `keyId` as
     * [BuildVpTokenRequest.keyId] so build validates and signs with the same key used during
     * preview. Build still re-validates against its effective signing key.
     *
     * Prefer [BuildVpTokenRequest.selectedCredentialOptions] (and optional
     * [BuildVpTokenRequest.selectedDisclosureOptions]) for the consent UI path.
     * [BuildVpTokenRequest.selectedCredentialIds] remains supported for the legacy
     * resolve → match → build flow.
     */
    suspend fun buildVpToken(
        wallet: Wallet,
        request: BuildVpTokenRequest,
        transactionDataTypeRegistry: TransactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
        clientIdTrustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
        resolveAuthorizationRequest: suspend (Url) -> ResolvedAuthorizationRequest = { requestUrl ->
            this@WalletPresentationHandler.resolveAuthorizationRequest(
                { WalletPresentationFormatRegistry.defaultCapabilities() },
                requestUrl,
                clientIdTrustConfiguration,
            )
        },
    ): BuildVpTokenResult {
        val keyMaterial = request.key?.key?.let { WalletKeyStoreEntry(it.getKeyId(), it, null) }
            ?: wallet.resolveKeyMaterial(request.keyId, setOf(KeyUsage.SIGN))
            ?: throw IllegalArgumentException("Wallet has no key available for VP token building")
        val authorizationRequest = resolveAndValidatePresentationRequest(
            requestUrl = request.requestUrl,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
            formatCapabilities = { keyMaterial.presentationCapabilities() },
            resolveAuthorizationRequest = resolveAuthorizationRequest,
        )
        val did = request.did ?: wallet.defaultDid()

        val dcqlQuery = authorizationRequest.dcqlQuery
            ?: throw IllegalArgumentException("AuthorizationRequest has no dcql_query")

        val selectedCredentialOptions = request.resolveSelectedCredentialOptions()
        selectedCredentialOptions.requireValidPresentationCredentialSelection()
        val selectedQueryIds = selectedCredentialOptions.mapTo(mutableSetOf()) { it.queryId }
        val requirements = dcqlQuery.requiredCredentialRequirements()
        require(requirements.satisfiedBy(selectedQueryIds)) {
            "Selected credential option(s) do not satisfy required presentation credential query constraints"
        }
        validateSelectedTransactionDataCredentials(
            authorizationRequest.transactionData.orEmpty(),
            selectedQueryIds,
        )

        val matched = selectFromStores(wallet, dcqlQuery, useWalletCredentialIds = true)
        val selected = matched.selectCredentialOptions(
            selectedCredentialOptions = selectedCredentialOptions,
            selectedDisclosureOptions = request.selectedDisclosureOptions,
        )
        require(requirements.satisfiedBy(selected.keys)) {
            "Selected credential option(s) do not match required presentation credential query constraints"
        }

        val crypto2Key = keyMaterial.crypto2Key
        val vpToken = if (crypto2Key != null) {
            WalletPresentFunctionality2.buildVpToken(
                authorizationRequest = authorizationRequest,
                matchedCredentials = selected,
                holderKey = crypto2Key,
                holderDid = did,
            )
        } else {
            WalletPresentFunctionality2.buildVpToken(
                authorizationRequest = authorizationRequest,
                matchedCredentials = selected,
                holderKey = requireNotNull(keyMaterial.legacyKey) {
                    "Key '${keyMaterial.keyId}' has no usable signing representation"
                },
                holderDid = did,
            )
        }
        val idToken = if (crypto2Key != null) {
            WalletPresentFunctionality2.buildIdToken(authorizationRequest, crypto2Key, did)
        } else {
            WalletPresentFunctionality2.buildIdToken(
                authorizationRequest,
                requireNotNull(keyMaterial.legacyKey),
                did,
            )
        }
        return BuildVpTokenResult(vpToken = vpToken, idToken = idToken)
    }

    /**
     * Isolated step 4: send the authorization response to the verifier.
     *
     * Re-resolves and revalidates [SendAuthorizationResponseRequest.requestUrl] so
     * `response_uri` / `response_mode` / encryption parameters are never taken from a
     * client-echoed [AuthorizationRequest].
     */
    suspend fun sendAuthorizationResponse(
        wallet: Wallet,
        request: SendAuthorizationResponseRequest,
        transactionDataTypeRegistry: TransactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
        clientIdTrustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
        resolveAuthorizationRequest: suspend (Url) -> ResolvedAuthorizationRequest = { requestUrl ->
            this@WalletPresentationHandler.resolveAuthorizationRequest(
                { WalletPresentationFormatRegistry.defaultCapabilities() },
                requestUrl,
                clientIdTrustConfiguration,
            )
        },
    ): WalletPresentResult {
        val authorizationRequest = resolveAndValidatePresentationRequest(
            requestUrl = request.requestUrl,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
            resolveAuthorizationRequest = resolveAuthorizationRequest,
        )
        return WalletPresentFunctionality2.sendAuthorizationResponse(
            authorizationRequest = authorizationRequest,
            vpToken = request.vpToken,
            idToken = request.idToken,
        ).getOrThrow()
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
        val matched = DcqlMatcher.match(query.copy(credentialSets = null), rawCredentials).getOrThrow()
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

    /**
     * @param capabilities resolved lazily: wallet metadata is only advertised when a `request_uri` is
     *   actually fetched, so a request carrying its object inline needs no signing key to be resolved.
     */
    private suspend fun resolveAuthorizationRequest(
        capabilities: () -> WalletPresentationFormatRegistry.RuntimeCapabilities,
        requestUrl: Url,
        clientIdTrustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
    ): ResolvedAuthorizationRequest {
        val fetcher = WebDataFetcher(WebDataFetcherId.OPENID4VP_WALLET_RESOLVE_AUTHORIZATIONREQUEST)
        return AuthorizationRequestResolver.resolve(
            requestUrl = requestUrl,
            unsignedRequestObjectPolicy = AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED,
            trustConfiguration = clientIdTrustConfiguration,
            fetchRequestUri = { requestUri, requestUriMethod ->
                AuthorizationRequestResolver.fetchRequestUriWithWebDataFetcher(
                    webResolveAuthReq = fetcher,
                    requestUri = requestUri,
                    requestUriMethod = requestUriMethod,
                    requestUriPostWalletMetadata = AuthorizationRequestResolver.buildRequestUriPostWalletMetadata(
                        WalletPresentationFormatRegistry.buildVpFormatsSupported(capabilities()),
                        clientIdTrustConfiguration,
                    ),
                )
            },
        )
    }

    /**
     * Re-resolves [requestUrl] and runs [PresentationRequestValidator], failing closed when
     * the request is invalid. Used by HTTP continuation steps that must not trust a
     * client-echoed [AuthorizationRequest].
     *
     * Key-independent request checks always run. Pass [formatCapabilities] derived from the
     * effective signing key when the caller will sign; omit it for non-signing steps such as
     * [sendAuthorizationResponse].
     */
    private suspend fun resolveAndValidatePresentationRequest(
        requestUrl: Url,
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
        formatCapabilities: (() -> WalletPresentationFormatRegistry.RuntimeCapabilities)? = null,
        resolveAuthorizationRequest: suspend (Url) -> ResolvedAuthorizationRequest,
    ): AuthorizationRequest {
        val resolvedAuthorizationRequest = resolveAuthorizationRequest(requestUrl)
        val validation = PresentationRequestValidator.validate(
            resolvedRequest = resolvedAuthorizationRequest,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
            formatCapabilities = formatCapabilities,
        )
        if (validation is PresentationRequestValidationResult.Invalid) {
            error(
                "Presentation request is invalid (${validation.error.code.code}): ${validation.error.message}"
            )
        }
        return resolvedAuthorizationRequest.authorizationRequest
    }

    internal suspend fun rememberPreviewedAuthorizationRequest(
        wallet: Wallet,
        preview: PreviewedPresentation,
    ): PresentationPreviewHandle = PresentationPreviewHandle(
        previewedAuthorizationRequests.create(
            walletId = wallet.id,
            value = preview,
        )
    )

    internal suspend fun consumePreviewedAuthorizationRequest(
        wallet: Wallet,
        handle: PresentationPreviewHandle,
        validate: (PreviewedPresentation) -> Unit = {},
    ): PreviewedPresentation = previewedAuthorizationRequests.consume(
        walletId = wallet.id,
        id = handle.value,
        validate = validate,
    )

    /** Explicitly discards a reviewed presentation without contacting the verifier. */
    suspend fun discardPreview(wallet: Wallet, handle: PresentationPreviewHandle) {
        previewedAuthorizationRequests.discard(walletId = wallet.id, id = handle.value)
    }

    /** Clears every presentation preview and tombstone owned by [wallet] during wallet deletion. */
    suspend fun clearPreviews(wallet: Wallet) {
        previewedAuthorizationRequests.clearWallet(wallet.id)
        previewedDcApiRequests.clearWallet(wallet.id)
    }

    private suspend fun rememberPreviewedDcApiRequest(
        wallet: Wallet,
        request: ResolvedDcApiRequest,
        allowedCredentialIds: Set<String>,
    ): String = previewedDcApiRequests.create(wallet.id, PreviewedDcApiRequest(request, allowedCredentialIds))

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

/**
 * Presentation capabilities of exactly this key material.
 *
 * Deliberately scoped to one key rather than to the union of every signing key in the wallet:
 * submission signs with a single key, so anything advertised to or accepted from a Verifier beyond
 * that key's capabilities could be accepted while previewing and then fail while signing.
 * Mirrors the capability computation in `WalletPresentFunctionality2.walletPresentHandlingWithKey`.
 */
/**
 * Defers the "a key is required" failure to the point where the key is actually used.
 *
 * The key is still selected once, before anything else, so every consumer sees the same key. But a
 * request that is rejected without ever needing a key - an untrusted client identifier, a malformed
 * request - must surface that rejection, not a wallet-local missing-key error.
 */
internal fun WalletKeyStoreEntry?.requiredOnUse(): () -> WalletKeyStoreEntry = {
    this ?: error("No key available: wallet has no keyStores and no staticKey")
}

private suspend fun resolvePreviewKeyMaterial(
    wallet: Wallet,
    request: PreviewPresentationRequest,
): WalletKeyStoreEntry? =
    request.key?.key?.let { WalletKeyStoreEntry(it.getKeyId(), it, null) }
        ?: wallet.resolveKeyMaterial(request.keyId, setOf(KeyUsage.SIGN))

internal fun WalletKeyStoreEntry.presentationCapabilities(): WalletPresentationFormatRegistry.RuntimeCapabilities =
    WalletPresentationFormatRegistry.capabilitiesFromKeys(
        keys = listOfNotNull(crypto2Key),
        fallbackKeyTypes = setOfNotNull(legacyKey?.keyType?.takeIf { crypto2Key == null }),
    )

// ---------------------------------------------------------------------------
// Isolated-step request / response types for the manual presentation flow
// ---------------------------------------------------------------------------

/**
 * Builds a VP token after re-resolving [requestUrl].
 *
 * [requestUrl] is re-resolved and revalidated server-side so DCQL / nonce / transaction
 * data are never taken from a client-echoed [AuthorizationRequest].
 *
 * Prefer [selectedCredentialOptions] (+ optional [selectedDisclosureOptions]) for consent UIs.
 * [selectedCredentialIds] remains for the legacy resolve → match → build path.
 *
 * After a stateless preview, pass the Ready `keyId` here so build uses the same signing key
 * that preview validated against.
 */
@Serializable
data class BuildVpTokenRequest(
    /**
     * Original OpenID4VP request URL from preview / resolve-request.
     * Re-resolved on every call; do not echo the preview [AuthorizationRequest].
     */
    override val requestUrl: Url,
    /**
     * User-selected credential options (queryId + credentialId), preferred for consent UIs.
     * When non-empty, takes precedence over [selectedCredentialIds].
     */
    val selectedCredentialOptions: List<PresentationCredentialSelection> = emptyList(),
    /** Optional selectively disclosable claim paths for the selected credentials. */
    val selectedDisclosureOptions: List<PresentationDisclosureSelection>? = null,
    /**
     * Legacy credential IDs grouped by DCQL query ID (from match-credentials-from-store).
     * Used when [selectedCredentialOptions] is empty.
     */
    val selectedCredentialIds: Map<String, List<String>> = emptyMap(),
    val key: DirectSerializedKey? = null,
    val keyId: String? = null,
    /** DID to use as holder binding. Defaults to the wallet's default DID. */
    val did: String? = null,
) : VpRequestSource

internal fun BuildVpTokenRequest.resolveSelectedCredentialOptions(): List<PresentationCredentialSelection> {
    if (selectedCredentialOptions.isNotEmpty()) return selectedCredentialOptions
    return selectedCredentialIds.flatMap { (queryId, credentialIds) ->
        credentialIds.map { credentialId ->
            PresentationCredentialSelection(queryId = queryId, credentialId = credentialId)
        }
    }
}

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
 * Final step of the manual presentation flow. [requestUrl] is re-resolved and revalidated
 * so response destination / mode / encryption come from the verifier, not the client.
 */
@Serializable
data class SendAuthorizationResponseRequest(
    /**
     * Original OpenID4VP request URL from preview.
     * Re-resolved on every call; do not echo the preview [AuthorizationRequest].
     */
    override val requestUrl: Url,
    /** The VP token from [buildVpToken]. */
    val vpToken: String,
    /** The ID token from [buildVpToken], or null. */
    val idToken: String? = null,
) : VpRequestSource
