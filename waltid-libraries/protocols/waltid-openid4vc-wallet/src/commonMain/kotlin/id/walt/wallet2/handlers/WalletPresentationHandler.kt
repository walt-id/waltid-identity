@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.handlers

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.SdJwtCredential
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.keys.KeyUsage
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.RequiredCredentialUnavailableException
import id.walt.dcql.models.DcqlQuery
import id.walt.holderpolicies.HolderPolicy
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.transactiondata.DecodedTransactionData
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.wallet2.consent.*
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletKeyStoreEntry
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.data.publicKeyThumbprint
import id.walt.wallet2.data.resolveHolderKey
import id.walt.wallet2.data.resolveKeyMaterial
import id.walt.wallet2.status.WalletCredentialStatusSnapshot
import id.walt.wallet2.status.reviewGuard
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import id.waltid.openid4vp.wallet.CredentialPresentationKey
import id.waltid.openid4vp.wallet.CredentialPresentationKeyResolver
import id.waltid.openid4vp.wallet.DcApiCredentialResponse
import id.waltid.openid4vp.wallet.DcApiRequestProtocol
import id.waltid.openid4vp.wallet.DcApiWallet
import id.waltid.openid4vp.wallet.PresentationRequestError
import id.waltid.openid4vp.wallet.PresentationRequestValidationResult
import id.waltid.openid4vp.wallet.PresentationRequestValidator
import id.waltid.openid4vp.wallet.PresentationValidationTransport
import id.waltid.openid4vp.wallet.ResolvedDcApiRequest
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult
import id.waltid.openid4vp.wallet.WalletPresentationFormatRegistry
import id.waltid.openid4vp.wallet.presentation.PresentationReviewGuard
import id.waltid.openid4vp.wallet.request.AuthenticatedClientFacts
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import id.waltid.openid4vp.wallet.response.ResponseEncryption
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlin.jvm.JvmInline
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

private val log = KotlinLogging.logger {}

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

/** Opaque identifier binding a presentation action to one reviewed request resolution. */
@Serializable
@JvmInline
value class PresentationPreviewHandle(val value: String) {
    init {
        require(value.isNotBlank()) { "Presentation preview handle must not be blank" }
    }

    override fun toString(): String = "PresentationPreviewHandle(<redacted>)"
}

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
        val owner: Wallet?
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
            override val owner: Wallet? = null,
        ) : PreviewedPresentation {
            internal var review: RetainedPresentationReview? = null
            internal val paymentConsent = RetainedPaymentConsent()
        }

        data class Invalid(
            override val requestUrl: Url,
            override val resolvedAuthorizationRequest: ResolvedAuthorizationRequest,
            val error: PresentationRequestError,
            override val owner: Wallet? = null,
        ) : PreviewedPresentation
    }

    private val previewedAuthorizationRequests =
        PreviewSessionStore<PreviewedPresentation>(sessionName = "Presentation")
    private val previewedDcApiRequests =
        PreviewSessionStore<PreviewedDcApiRequest>(sessionName = "Digital Credentials presentation")

    private data class PreviewedDcApiRequest(
        val owner: Wallet,
        val request: ResolvedDcApiRequest,
        val allowedCredentialIds: Set<String>,
    ) {
        val paymentConsent = RetainedPaymentConsent()
    }

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
        // Defaulted so a caller that only cares about transaction data need not opt into session
        // notifications, matching every other overload here.
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
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
        expectedRequestObjectAudience: String = AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE,
        unsignedRequestObjectPolicy: AuthorizationRequestResolver.UnsignedRequestObjectPolicy =
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
    ): WalletPresentResult = presentCredentialWithTrust(
        wallet, request, holderPoliciesToRun = null, onEvent, transactionDataTypeRegistry,
        clientIdTrustConfiguration, beforeCredentialsUsed, expectedRequestObjectAudience, unsignedRequestObjectPolicy,
    )

    /** When requested and supplied, [holderPoliciesToRun] must allow the selected stored credentials. */
    suspend fun presentCredentialWithTrust(
        wallet: Wallet,
        request: PresentCredentialRequest,
        holderPoliciesToRun: Flow<HolderPolicy>?,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
        clientIdTrustConfiguration: ClientIdTrustConfiguration,
        beforeCredentialsUsed: suspend (Int) -> Unit = {},
        expectedRequestObjectAudience: String = AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE,
        unsignedRequestObjectPolicy: AuthorizationRequestResolver.UnsignedRequestObjectPolicy =
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
    ): WalletPresentResult {
        val keyMaterial = request.key?.key?.let { WalletKeyStoreEntry(it.getKeyId(), it, null) }
            ?: wallet.resolveKeyMaterial(request.keyId, setOf(KeyUsage.SIGN))
            ?: error("No key available: wallet has no keyStores, no staticKey, and no keyId was specified")
        val did = request.did ?: wallet.defaultDid()
        log.trace { "presentCredential: keyId=${keyMaterial.keyId}, did=$did, requestUrl=${request.requestUrl}" }

        onEvent(WalletSessionEvent.presentation_request_parsed)

        val result = presentWithKeyMaterial(
            wallet = wallet,
            keyMaterial = keyMaterial,
            holderDid = did,
            presentationRequestUrl = request.requestUrl,
            selectCredentialsForQuery = { query ->
                log.trace { "Selecting credentials for DCQL query: ${query.credentials.map { it.id }}" }
                // Timed and counted: the presentation flow is believed to run this selection more than
                // once per presentation (see submitPresentation and buildVpToken), and each pass reads,
                // parses and DCQL-matches the wallet's credentials again.
                val selectionStart = TimeSource.Monotonic.markNow()
                selectPresentableFromStores(wallet, query)
                    .also { matched ->
                        log.debug { "DCQL selection pass took ${selectionStart.elapsedNow()}" }
                        log.trace { "DCQL matched queryIds: ${matched.keys}" }
                        onEvent(WalletSessionEvent.presentation_credentials_selected)
                    }
            },
            runPolicies = request.runPolicies,
            holderPoliciesToRun = holderPoliciesToRun,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
            clientIdTrustConfiguration = clientIdTrustConfiguration,
            beforeCredentialsUsed = beforeCredentialsUsed,
            expectedRequestObjectAudience = expectedRequestObjectAudience,
            unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
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
        // Defaulted so a caller that only cares about transaction data need not opt into session
        // notifications, matching every other overload here.
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
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
        expectedRequestObjectAudience: String = AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE,
        unsignedRequestObjectPolicy: AuthorizationRequestResolver.UnsignedRequestObjectPolicy =
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
    ): WalletPresentResult = presentCredentialIsolatedWithTrust(
        wallet, request, holderPoliciesToRun = null, onEvent, transactionDataTypeRegistry,
        clientIdTrustConfiguration, beforeCredentialsUsed, expectedRequestObjectAudience, unsignedRequestObjectPolicy,
    )

    /** When supplied, [holderPoliciesToRun] must allow the selected inline credentials. */
    suspend fun presentCredentialIsolatedWithTrust(
        wallet: Wallet,
        request: PresentCredentialIsolatedRequest,
        holderPoliciesToRun: Flow<HolderPolicy>?,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
        clientIdTrustConfiguration: ClientIdTrustConfiguration,
        beforeCredentialsUsed: suspend (Int) -> Unit = {},
        expectedRequestObjectAudience: String = AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE,
        unsignedRequestObjectPolicy: AuthorizationRequestResolver.UnsignedRequestObjectPolicy =
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
    ): WalletPresentResult {
        val keyMaterial = request.key?.key?.let { WalletKeyStoreEntry(it.getKeyId(), it, null) }
            ?: wallet.resolveKeyMaterial(request.keyId, setOf(KeyUsage.SIGN))
            ?: error("No key available for isolated presentation")
        val did = request.did ?: wallet.defaultDid()

        onEvent(WalletSessionEvent.presentation_request_parsed)

        val isolatedCredentialsById = request.credentials.mapIndexed { idx, stored -> idx.toString() to stored }.toMap()

        val result = presentWithKeyMaterial(
            wallet = wallet,
            keyMaterial = keyMaterial,
            holderDid = did,
            presentationRequestUrl = request.requestUrl,
            selectCredentialsForQuery = { query ->
                val candidates = isolatedCredentialsById.mapNotNull { (id, stored) -> stored.toDcqlCandidate(id, query) }
                DcqlMatcher.match(query, candidates.map { it.credential }, candidates.matchingContext()).getOrThrow()
                    .also { onEvent(WalletSessionEvent.presentation_credentials_selected) }
            },
            runPolicies = holderPoliciesToRun != null,
            holderPoliciesToRun = holderPoliciesToRun,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
            clientIdTrustConfiguration = clientIdTrustConfiguration,
            beforeCredentialsUsed = beforeCredentialsUsed,
            isolatedCredentialsById = isolatedCredentialsById,
            expectedRequestObjectAudience = expectedRequestObjectAudience,
            unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
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
        expectedRequestObjectAudience: String = AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE,
        unsignedRequestObjectPolicy: AuthorizationRequestResolver.UnsignedRequestObjectPolicy =
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
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
                    expectedRequestObjectAudience,
                    unsignedRequestObjectPolicy,
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
        expectedRequestObjectAudience: String = AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE,
        unsignedRequestObjectPolicy: AuthorizationRequestResolver.UnsignedRequestObjectPolicy =
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
        resolveAuthorizationRequest: (suspend (Url) -> ResolvedAuthorizationRequest)? = null,
    ): StatelessPreviewPresentationResult {
        val keyMaterial = resolvePreviewKeyMaterial(wallet, request).requiredOnUse()
        val resolveRequest = resolveAuthorizationRequest ?: { requestUrl ->
            this@WalletPresentationHandler.resolveAuthorizationRequest(
                { keyMaterial().presentationCapabilities() },
                requestUrl,
                clientIdTrustConfiguration,
                expectedRequestObjectAudience,
                unsignedRequestObjectPolicy,
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
        val responseEncryption = ResponseEncryption.resolveCrypto2(resolvedAuthorizationRequest)?.metadata()
        val storedById = wallet.streamAllCredentials().toList().associateBy { it.id }
        val holderKeyEligibility = selectHolderKeyEligibleFromSnapshot(
            wallet = wallet,
            query = query,
            storedById = storedById,
        )
        val matched = holderKeyEligibility.matched
        val availableCredentialQueryIds = matched.filterValues { it.isNotEmpty() }.keys
        val availabilityError = PresentationRequestValidator.validateTransactionDataCredentialAvailability(
            transactionData = valid.transactionData,
            availableCredentialQueryIds = availableCredentialQueryIds,
        ) ?: PresentationRequestValidator.validateCredentialAvailability(
            query = query,
            availableCredentialQueryIds = availableCredentialQueryIds,
        )
        if (availabilityError != null) {
            holderKeyEligibility.bindingFailureForUnavailablePresentation(
                transactionDataCredentialIdGroups = valid.transactionData.map { it.transactionData.credentialIds },
                requirements = query.requiredCredentialRequirements(),
                availableCredentialQueryIds = availableCredentialQueryIds,
            )?.let { throw it }
            PresentationRequestValidator.requireErrorResponseCanBeSent(resolvedAuthorizationRequest, availabilityError)
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
                    metadata = stored.metadata,
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
                    owner = wallet,
                    requestUrl = request.requestUrl,
                    resolvedAuthorizationRequest = resolvedAuthorizationRequest,
                    error = validation.error,
                ),
            )
            return PreviewPresentationResult.Invalid(handle, resolvedAuthorizationRequest, validation.error)
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
        val responseEncryption = ResponseEncryption.resolveCrypto2(resolvedAuthorizationRequest)?.metadata()
        val storedById = wallet.streamAllCredentials().toList().associateBy { it.id }
        val holderKeyEligibility = selectHolderKeyEligibleFromSnapshot(
            wallet = wallet,
            query = query,
            storedById = storedById,
        )
        val matched = holderKeyEligibility.matched
        val availableCredentialQueryIds = matched.filterValues { it.isNotEmpty() }.keys
        val availabilityError = PresentationRequestValidator.validateTransactionDataCredentialAvailability(
            transactionData = valid.transactionData,
            availableCredentialQueryIds = availableCredentialQueryIds,
        ) ?: PresentationRequestValidator.validateCredentialAvailability(
            query = query,
            availableCredentialQueryIds = availableCredentialQueryIds,
        )
        if (availabilityError != null) {
            holderKeyEligibility.bindingFailureForUnavailablePresentation(
                transactionDataCredentialIdGroups = valid.transactionData.map { it.transactionData.credentialIds },
                requirements = query.requiredCredentialRequirements(),
                availableCredentialQueryIds = availableCredentialQueryIds,
            )?.let { throw it }
            PresentationRequestValidator.requireErrorResponseCanBeSent(resolvedAuthorizationRequest, availabilityError)
            val handle = rememberPreviewedAuthorizationRequest(
                wallet = wallet,
                preview = PreviewedPresentation.Invalid(
                    owner = wallet,
                    requestUrl = request.requestUrl,
                    resolvedAuthorizationRequest = resolvedAuthorizationRequest,
                    error = availabilityError,
                ),
            )
            return PreviewPresentationResult.Invalid(handle, resolvedAuthorizationRequest, availabilityError)
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
                    metadata = stored.metadata,
                )
            }
        }
        val handle = rememberPreviewedAuthorizationRequest(
            wallet = wallet,
            preview = PreviewedPresentation.Ready(
                owner = wallet,
                requestUrl = request.requestUrl,
                resolvedAuthorizationRequest = resolvedAuthorizationRequest,
                keyId = executionKey().keyId,
            ),
        )
        return PreviewPresentationResult.Ready(
            handle = handle,
            resolvedAuthorizationRequest = resolvedAuthorizationRequest,
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
        clientIdTrustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
    ): PreviewDcApiPresentationResult {
        onEvent(WalletSessionEvent.presentation_request_parsed)
        val resolvedRequest = DcApiWallet.resolveRequest(
            protocol = request.protocol,
            data = request.data,
            origin = request.origin,
            trustConfiguration = clientIdTrustConfiguration,
        )
        val authorizationRequest = resolvedRequest.authorizationRequest
        val keyMaterial = wallet.resolveKeyMaterial(keyId = null, crypto2Usages = setOf(KeyUsage.SIGN))
        val defaultCapabilities = keyMaterial?.presentationCapabilities()
            ?: WalletPresentationFormatRegistry.defaultCapabilities()
        val resolveMdocHolderKey = wallet.mdocHolderKeyResolver()
        // Structure and authentication are checked before any credential is read. Format
        // compatibility waits until the snapshot below: an mdoc is signed by its bound device
        // key, which can differ from the wallet default, so the default key alone must not
        // reject an mdoc-only request.
        val requestForValidation = ResolvedAuthorizationRequest.Plain(
            authorizationRequest = authorizationRequest,
            client = AuthenticatedClientFacts(
                effectiveClientMetadata = resolvedRequest.effectiveClientMetadata,
                responseDestinationAuthenticated = true,
                boundResponseDestination = resolvedRequest.origin,
            ),
        )
        val validation = PresentationRequestValidator.validate(
            resolvedRequest = requestForValidation,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
            formatCapabilities = null,
            transport = PresentationValidationTransport.DigitalCredentialsApi,
        )
        if (validation is PresentationRequestValidationResult.Invalid) {
            throw IllegalArgumentException(validation.error.message)
        }
        val valid = validation as PresentationRequestValidationResult.Valid
        val query = requireNotNull(authorizationRequest.dcqlQuery)
        val transactionDataItems = valid.transactionData
        // response_mode=dc_api.jwt is unanswerable without usable verifier encryption metadata, and
        // the mdoc session transcript is thumbprinted from the same key. Resolving it here rather
        // than at response-build time means an unusable configuration is rejected before any
        // credential is read, so a verifier cannot get a consent dialog - and the disclosure of what
        // the wallet holds that comes with it - for a request it could never have received an answer
        // to. The value is not retained: submission re-resolves it from the same immutable retained
        // Authorization Request, so there is nothing to keep in sync.
        if (authorizationRequest.responseMode == OpenID4VPResponseMode.DC_API_JWT) {
            val encryption = requireNotNull(ResponseEncryption.resolveCrypto2(resolvedRequest)) {
                "response_mode=dc_api.jwt requires client_metadata response-encryption keys"
            }
            // Thumbprinting is what canonicalizes the published coordinates, and it is the same value
            // the mdoc session transcript binds to, so taking it here rejects key material that is
            // well-formed enough to be selected but cannot actually be encrypted to.
            encryption.thumbprint()
        }
        val storedCredentials = wallet.streamAllCredentials().toList()
        val formatCapabilities = defaultCapabilities.withBoundMdocDeviceAuthentication(
            stored = storedCredentials,
            resolveMdocHolderKey = resolveMdocHolderKey,
        )
        val formatValidation = PresentationRequestValidator.validate(
            resolvedRequest = requestForValidation,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
            formatCapabilities = { formatCapabilities },
            transport = PresentationValidationTransport.DigitalCredentialsApi,
        )
        if (formatValidation is PresentationRequestValidationResult.Invalid) {
            throw IllegalArgumentException(formatValidation.error.message)
        }
        val storedById = storedCredentials.associateBy { it.id }
        val holderKeyEligibility = selectHolderKeyEligibleFromSnapshot(
            wallet = wallet,
            query = query,
            storedById = storedById,
            allowedCredentialIds = request.eligibleCredentialIds,
        )
        val matched = holderKeyEligibility.matched
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
                    metadata = stored.metadata,
                )
            }
        }.filter { option ->
            request.eligibleCredentialIds?.let { option.credentialId in it } ?: true
        }.compatibleWithVerifierFormats(
            verifierFormats = resolvedRequest.effectiveClientMetadata?.vpFormatsSupported,
        ) { option ->
            val format = WalletPresentationFormatRegistry.resolve(option.format)
            if (format != WalletPresentationFormatRegistry.SupportedFormat.MSO_MDOC) {
                defaultCapabilities
            } else {
                storedById[option.credentialId]?.mdocCapabilities(resolveMdocHolderKey)
            }
        }
        val credentialRequirements = query.requiredCredentialRequirements()
        val offeredQueryIds = credentialOptions.mapTo(mutableSetOf()) { it.queryId }
        val transactionAvailabilityError = PresentationRequestValidator.validateTransactionDataCredentialAvailability(
            transactionData = transactionDataItems,
            availableCredentialQueryIds = offeredQueryIds,
        )
        if (transactionAvailabilityError != null) {
            holderKeyEligibility.bindingFailureForUnavailableTransactionData(
                credentialIdGroups = transactionDataItems.map { it.transactionData.credentialIds },
                availableCredentialQueryIds = offeredQueryIds,
            )?.let { throw it }
            throw IllegalArgumentException(transactionAvailabilityError.message)
        }
        if (!credentialRequirements.satisfiedBy(offeredQueryIds)) {
            holderKeyEligibility.bindingFailureForUnsatisfiedRequirements(
                requirements = credentialRequirements,
                availableCredentialQueryIds = offeredQueryIds,
            )?.let { throw it }
        }
        if (credentialOptions.isEmpty()) {
            holderKeyEligibility.firstBindingFailure()?.let { throw it }
        }
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

    /** Captures the complete remote disclosure under one exclusive preview lease. */
    suspend fun retainPresentationReview(
        wallet: Wallet, selection: SubmitPresentationRequest, policy: PaymentConsentPolicy,
    ): RetainedPresentationReview = previewedAuthorizationRequests.prepare(wallet.id, selection.previewHandle.value) { preview ->
        require(preview is PreviewedPresentation.Ready && preview.owner === wallet) { "Presentation belongs to another wallet instance" }
        preview.review = null
        require(selection.keyId == null || selection.keyId == preview.keyId) { "The signing key must match the preview" }
        val request = selection.copy(selectedCredentialOptions = selection.selectedCredentialOptions.toList(),
            selectedDisclosureOptions = selection.selectedDisclosureOptions?.toList())
        val authorization = preview.resolvedAuthorizationRequest.authorizationRequest
        val query = requireNotNull(authorization.dcqlQuery)
        suspend fun currentSelection() = selectReviewedCredentials(wallet, query, request.selectedCredentialOptions, request.selectedDisclosureOptions)
        val selected = currentSelection()
        validateSelectedTransactionDataCredentials(authorization.transactionData.orEmpty(), selected.matches.keys)
        val claims = preparePresentationReview(selected.matches)
        val identities = selected.credentialIdentities()
        val metadata = selected.typeMetadataSnapshots.mapValues { it.value.toString() }
        val did = request.did ?: wallet.defaultDid()
        val key = requireNotNull(wallet.resolveKeyMaterial(preview.keyId, setOf(KeyUsage.SIGN))) { "Reviewed signing key is unavailable" }
        val thumbprint = key.publicKeyThumbprint()
        val ids = request.selectedCredentialOptions.mapTo(mutableSetOf()) { it.credentialId }
        val statusService = wallet.credentialStatuses()
        val snapshot = statusService.snapshot()
        val statuses = snapshot.credentials.filter { it.reference.credentialId in ids }
        val statusGuard = statusService.reviewGuard(ids, statuses.associate { it.reference.credentialId to it.presentation.advisoryRevision })
        statusGuard.check()
        val payment = preview.paymentConsent.prepare(authorization, selected.matches, request.selectedCredentialOptions,
            request.selectedDisclosureOptions, key, policy, holderDid = did,
            requiresUnsignedRequestWarning = preview.resolvedAuthorizationRequest !is ResolvedAuthorizationRequest.AuthenticatedRequestObject,
            typeMetadataSnapshots = selected.typeMetadataSnapshots)
        val guard = PresentationReviewGuard {
            statusGuard.check()
            val currentKey = wallet.resolveKeyMaterial(preview.keyId, setOf(KeyUsage.SIGN))
            if (currentKey == null || currentKey.publicKeyThumbprint() != thumbprint || (request.did ?: wallet.defaultDid()) != did)
                throw PresentationReviewChangedException()
            val current = try {
                currentSelection()
            } catch (_: IllegalArgumentException) {
                throw PresentationReviewChangedException()
            } catch (failure: IllegalStateException) {
                if (failure is CancellationException) throw failure
                throw PresentationReviewChangedException()
            }
            if (current.credentialIdentities() != identities || current.typeMetadataSnapshots.mapValues { it.value.toString() } != metadata ||
                preparePresentationReview(current.matches).credentials != claims.credentials) throw PresentationReviewChangedException()
            preview.paymentConsent.confirm(payment?.revision, authorization, current.matches, request.selectedCredentialOptions,
                request.selectedDisclosureOptions, currentKey, policy, holderDid = did, consume = false)
        }
        RetainedPresentationReview(claims, WalletCredentialStatusSnapshot(snapshot.evaluatedAt, statuses), payment, wallet, request.copy(paymentConsentRevision = payment?.revision), guard)
            .also { preview.review = it }
    }

    private fun CredentialSelection.credentialIdentities(): Map<String, List<Pair<String, String>>> =
        matches.mapValues { (_, results) -> results.map { match ->
            val raw = match.credential as RawDcqlCredential
            val credential = raw.originalCredential as DigitalCredential
            raw.id to ((credential as? SdJwtCredential)?.signedWithDisclosures ?: credential.signed ?: credential.credentialData.toString())
        } }

    /** Recomputes effective claims under the existing preview lease without signing or consuming it. */
    suspend fun reviewPresentation(
        wallet: Wallet,
        request: SubmitPresentationRequest,
    ): PreparedPresentationReview = previewedAuthorizationRequests.prepare(wallet.id, request.previewHandle.value) { preview ->
        require(preview is PreviewedPresentation.Ready) { "Cannot review an invalid presentation" }
        require(request.keyId == null || request.keyId == preview.keyId) { "The signing key must match the reviewed key" }
        val authorization = preview.resolvedAuthorizationRequest.authorizationRequest
        val selected = selectReviewedCredentials(wallet, requireNotNull(authorization.dcqlQuery),
            request.selectedCredentialOptions, request.selectedDisclosureOptions)
        validateSelectedTransactionDataCredentials(authorization.transactionData.orEmpty(), selected.matches.keys)
        preparePresentationReview(selected.matches)
    }

    /** Selection review for a wallet-bound Digital Credentials API request; does not consume it. */
    suspend fun reviewDcApiPresentation(
        wallet: Wallet,
        request: SubmitDcApiPresentationRequest,
    ): PreparedPresentationReview = previewedDcApiRequests.prepare(wallet.id, request.requestId) { preview ->
        require(request.selectedCredentialOptions.all { it.credentialId in preview.allowedCredentialIds }) {
            "A selected credential was not offered by the retained Digital Credentials preview"
        }
        val authorization = preview.request.authorizationRequest
        val selected = selectReviewedCredentials(wallet, requireNotNull(authorization.dcqlQuery),
            request.selectedCredentialOptions, request.selectedDisclosureOptions)
        validateSelectedTransactionDataCredentials(authorization.transactionData.orEmpty(), selected.matches.keys)
        preparePresentationReview(selected.matches)
    }

    /** Prepares metadata-driven consent under the retained URL preview's exclusive lease. */
    suspend fun preparePaymentConsent(
        wallet: Wallet,
        request: SubmitPresentationRequest,
        policy: PaymentConsentPolicy,
    ): PreparedPaymentConsent? = previewedAuthorizationRequests.prepare(wallet.id, request.previewHandle.value) { preview ->
        require(preview is PreviewedPresentation.Ready) { "Cannot prepare an invalid presentation" }
        require(request.keyId == null || request.keyId == preview.keyId) { "The signing key must match the reviewed key" }
        val authorization = preview.resolvedAuthorizationRequest.authorizationRequest
        val key = wallet.resolveKeyMaterial(preview.keyId, setOf(KeyUsage.SIGN)) ?: error("Reviewed signing key is unavailable")
        val selected = selectReviewedCredentials(wallet, requireNotNull(authorization.dcqlQuery),
            request.selectedCredentialOptions, request.selectedDisclosureOptions)
        validateSelectedTransactionDataCredentials(authorization.transactionData.orEmpty(), selected.matches.keys)
        preview.paymentConsent.prepare(authorization, selected.matches, request.selectedCredentialOptions,
            request.selectedDisclosureOptions, key, policy,
            holderDid = request.did ?: wallet.defaultDid(),
            requiresUnsignedRequestWarning = preview.resolvedAuthorizationRequest !is ResolvedAuthorizationRequest.AuthenticatedRequestObject,
            typeMetadataSnapshots = selected.typeMetadataSnapshots)
    }

    /** Prepares metadata-driven consent for an existing DC API preview without consuming it. */
    suspend fun prepareDcApiPaymentConsent(
        wallet: Wallet,
        request: SubmitDcApiPresentationRequest,
        policy: PaymentConsentPolicy,
    ): PreparedPaymentConsent? = previewedDcApiRequests.prepare(wallet.id, request.requestId) { preview ->
        require(request.selectedCredentialOptions.all { it.credentialId in preview.allowedCredentialIds }) {
            "A selected credential was not offered by the retained Digital Credentials preview"
        }
        val authorization = preview.request.authorizationRequest
        val key = wallet.resolveKeyMaterial(null, setOf(KeyUsage.SIGN)) ?: error("Signing key is unavailable")
        val selected = selectReviewedCredentials(wallet, requireNotNull(authorization.dcqlQuery),
            request.selectedCredentialOptions, request.selectedDisclosureOptions)
        validateSelectedTransactionDataCredentials(authorization.transactionData.orEmpty(), selected.matches.keys)
        preview.paymentConsent.prepare(authorization, selected.matches, request.selectedCredentialOptions,
            request.selectedDisclosureOptions, key, policy,
            holderDid = request.did ?: wallet.defaultDid(),
            requiresUnsignedRequestWarning = preview.request.protocol == DcApiRequestProtocol.OPENID4VP_V1_UNSIGNED,
            typeMetadataSnapshots = selected.typeMetadataSnapshots)
    }

    /** Dismissal cancels any active DC API authorization and invalidates its consent. */
    suspend fun discardDcApiPreview(wallet: Wallet, requestId: String) {
        previewedDcApiRequests.discard(wallet.id, requestId)
    }

    private suspend fun selectReviewedCredentials(
        wallet: Wallet,
        query: DcqlQuery,
        credentials: List<PresentationCredentialSelection>,
        disclosures: List<PresentationDisclosureSelection>?,
    ): CredentialSelection {
        credentials.requireValidPresentationCredentialSelection()
        val requirements = query.requiredCredentialRequirements()
        require(requirements.satisfiedBy(credentials.mapTo(mutableSetOf()) { it.queryId })) {
            "Selected credentials do not satisfy the presentation requirements"
        }
        val selection = selectFromSnapshot(wallet, query, wallet.streamAllCredentials().toList(),
            useWalletCredentialIds = true, eligibleCredentialIds = credentials.mapTo(mutableSetOf()) { it.credentialId })
        val selected = selection.matches.selectCredentialOptions(credentials, disclosures)
        require(requirements.satisfiedBy(selected.keys)) { "Selected credentials no longer match the presentation requirements" }
        return selection.copy(matches = selected)
    }

    suspend fun submitPresentation(
        wallet: Wallet,
        request: SubmitPresentationRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
    ): WalletPresentResult = submitPresentation(
        wallet = wallet,
        request = request,
        onEvent = onEvent,
        transactionDataTypeRegistry = transactionDataTypeRegistry,
        scaAuthorizer = null,
    )

    suspend fun submitStatusReviewedPresentation(
        wallet: Wallet, request: SubmitPresentationRequest,
        reviewedStatus: Map<String, String>,
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        scaAuthorizer: WalletScaPresentationAuthorizer? = null,
        paymentConsentPolicy: PaymentConsentPolicy? = null,
    ): WalletPresentResult {
        val submission = request.copy(
            selectedCredentialOptions = request.selectedCredentialOptions.toList(),
            selectedDisclosureOptions = request.selectedDisclosureOptions?.toList(),
        )
        val revisions = reviewedStatus.toMap()
        val guard = wallet.credentialStatuses().reviewGuard(
            submission.selectedCredentialOptions.mapTo(mutableSetOf()) { it.credentialId }, revisions)
        guard.check()
        return withContext(guard) {
            submitPresentation(wallet, submission, onEvent, transactionDataTypeRegistry, scaAuthorizer, paymentConsentPolicy)
        }
    }

    suspend fun submitPresentation(
        wallet: Wallet,
        request: SubmitPresentationRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
        scaAuthorizer: WalletScaPresentationAuthorizer?,
        paymentConsentPolicy: PaymentConsentPolicy? = null,
    ): WalletPresentResult = submitResolvedPresentation(wallet, request, onEvent, transactionDataTypeRegistry, scaAuthorizer, paymentConsentPolicy, null)

    /** Submits precisely the wallet-issued review with freshness checks before signing and delivery. */
    suspend fun submitReviewedPresentation(
        wallet: Wallet, review: RetainedPresentationReview,
        transactionDataTypeRegistry: TransactionDataTypeRegistry, scaAuthorizer: WalletScaPresentationAuthorizer?,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
    ): WalletPresentResult {
        require(review.owner === wallet) { "Review belongs to another wallet instance" }
        return withContext(review.guard) {
            submitResolvedPresentation(wallet, review.request, onEvent, transactionDataTypeRegistry, scaAuthorizer, null, review)
        }
    }

    private suspend fun submitResolvedPresentation(
        wallet: Wallet, request: SubmitPresentationRequest, onEvent: suspend (WalletSessionEvent) -> Unit,
        transactionDataTypeRegistry: TransactionDataTypeRegistry, scaAuthorizer: WalletScaPresentationAuthorizer?,
        paymentConsentPolicy: PaymentConsentPolicy?, review: RetainedPresentationReview?,
    ): WalletPresentResult {
        request.selectedCredentialOptions.requireValidPresentationCredentialSelection()
        val statusReviewed = currentCoroutineContext()[PresentationReviewGuard] != null
        suspend fun execute(block: suspend (PreviewedPresentation) -> WalletPresentResult): WalletPresentResult =
            if (statusReviewed) previewedAuthorizationRequests.useStatusReviewedOnce(wallet.id, request.previewHandle.value, validate = {
                if (review != null && (it as? PreviewedPresentation.Ready)?.review !== review) throw PresentationReviewChangedException()
            }, block = block)
            else previewedAuthorizationRequests.useOnce(wallet.id, request.previewHandle.value, validate = { cached ->
                require(cached is PreviewedPresentation.Ready) { "Cannot submit an invalid presentation request; reject it or dismiss it locally" }
            }, block = block)
        return execute { preview ->
            review?.guard?.check()
            val ready = preview as? PreviewedPresentation.Ready
                ?: error("Unexpected presentation preview state")
            require(request.keyId == null || request.keyId == ready.keyId) { "The signing key must match the reviewed key" }
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

            val selected = selectReviewedCredentials(wallet, requireNotNull(resolvedAuthorizationRequest.authorizationRequest.dcqlQuery),
                request.selectedCredentialOptions, request.selectedDisclosureOptions)
            paymentConsentPolicy?.let { policy ->
                ready.paymentConsent.confirm(request.paymentConsentRevision, resolvedAuthorizationRequest.authorizationRequest,
                    selected.matches, request.selectedCredentialOptions, request.selectedDisclosureOptions, keyMaterial, policy, holderDid = did)
            }

            val result = presentWithKeyMaterial(
                wallet = wallet,
                keyMaterial = keyMaterial,
                holderDid = did,
                presentationRequestUrl = preview.requestUrl,
                resolvedAuthorizationRequest = resolvedAuthorizationRequest,
                selectCredentialsForQuery = {
                    onEvent(WalletSessionEvent.presentation_credentials_selected)
                    selected.matches
                },
                runPolicies = request.runPolicies,
                transactionDataTypeRegistry = transactionDataTypeRegistry,
                scaAuthorizer = scaAuthorizer,
            )

            result.emitPresentationOutcome(onEvent)
        }
    }

    /**
     * Rejects exactly one reviewed presentation and atomically consumes its preview handle.
     */
    suspend fun rejectPresentation(
        wallet: Wallet,
        request: RejectPresentationRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
    ): WalletPresentResult = rejectPreview(wallet, request, onEvent) {}

    /** Rejects a preview issued by this exact open wallet instance. */
    suspend fun rejectInstancePresentation(
        wallet: Wallet,
        request: RejectPresentationRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
    ): WalletPresentResult = rejectPreview(wallet, request, onEvent) { require(it.owner === wallet) { "Preview belongs to another wallet instance" } }

    private suspend fun rejectPreview(
        wallet: Wallet,
        request: RejectPresentationRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit,
        validateOwner: (PreviewedPresentation) -> Unit,
    ): WalletPresentResult {
        val preview = consumePreviewedAuthorizationRequest(wallet, request.previewHandle) { cached ->
            validateOwner(cached)
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
        expectedRequestObjectAudience: String = AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE,
        unsignedRequestObjectPolicy: AuthorizationRequestResolver.UnsignedRequestObjectPolicy =
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
    ): WalletPresentResult {
        val resolvedAuthorizationRequest = resolveAuthorizationRequest(
            { WalletPresentationFormatRegistry.defaultCapabilities() },
            request.requestUrl,
            clientIdTrustConfiguration,
            expectedRequestObjectAudience,
            unsignedRequestObjectPolicy,
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

    /**
     * Wrap a selection lambda so one presentation selects at most once per query.
     *
     * The presentation flow asks for the selection repeatedly - resolving the request, checking DCQL
     * fulfilment and building the vp_token each ask again - which measured **7.6 selection passes per
     * presentation at ~8ms each**, roughly 15ms of a 51ms presentation. Every pass reads the credential
     * stores, parses each credential and re-runs DCQL matching, and it was 8ms even for a wallet holding a
     * single credential, so the waste grows with wallet size.
     *
     * Caching is sound rather than merely convenient: the wallet cannot change during one presentation, so
     * the same query must yield the same match. The cache lives exactly as long as the call.
     */
    private fun memoizedSelection(
        select: suspend (DcqlQuery) -> Map<String, List<DcqlMatcher.DcqlMatchResult>>,
    ): suspend (DcqlQuery) -> Map<String, List<DcqlMatcher.DcqlMatchResult>> {
        val selected = mutableMapOf<DcqlQuery, Map<String, List<DcqlMatcher.DcqlMatchResult>>>()
        val selectionMutex = Mutex()
        return { query ->
            selectionMutex.withLock {
                selected[query] ?: select(query).also { selected[query] = it }
            }
        }
    }

    private suspend fun presentWithKeyMaterial(
        wallet: Wallet,
        keyMaterial: WalletKeyStoreEntry,
        holderDid: String?,
        presentationRequestUrl: Url,
        selectCredentialsForQuery: suspend (DcqlQuery) -> Map<String, List<DcqlMatcher.DcqlMatchResult>>,
        runPolicies: Boolean?,
        holderPoliciesToRun: Flow<HolderPolicy>? = null,
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
        resolvedAuthorizationRequest: ResolvedAuthorizationRequest? = null,
        clientIdTrustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
        beforeCredentialsUsed: suspend (Int) -> Unit = {},
        isolatedCredentialsById: Map<String, StoredCredential> = emptyMap(),
        expectedRequestObjectAudience: String = AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE,
        unsignedRequestObjectPolicy: AuthorizationRequestResolver.UnsignedRequestObjectPolicy =
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
        scaAuthorizer: WalletScaPresentationAuthorizer? = null,
    ): Result<WalletPresentResult> = memoizedSelection(selectCredentialsForQuery).let { selectOnce ->
        keyMaterial.crypto2Key?.let { crypto2Key ->
            WalletPresentFunctionality2.walletPresentHandling(
                holderKey = crypto2Key,
                holderDid = holderDid,
                presentationRequestUrl = presentationRequestUrl,
                selectCredentialsForQuery = selectOnce,
                holderPoliciesToRun = holderPoliciesToRun,
                runPolicies = runPolicies,
                transactionDataTypeRegistry = transactionDataTypeRegistry,
                resolvedAuthorizationRequest = resolvedAuthorizationRequest,
                clientIdTrustConfiguration = clientIdTrustConfiguration,
                beforeCredentialsUsed = beforeCredentialsUsed,
                expectedRequestObjectAudience = expectedRequestObjectAudience,
                unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
                scaAuthorizer = scaAuthorizer?.let { authorizer ->
                    authorizer.forKey(crypto2Key)
                },
                credentialHolderKeyResolver = wallet.credentialHolderKeyResolver(isolatedCredentialsById, scaAuthorizer),
            )
        } ?: WalletPresentFunctionality2.walletPresentHandling(
            holderKey = requireNotNull(keyMaterial.legacyKey) {
                "Key '${keyMaterial.keyId}' has no usable signing representation"
            },
            holderDid = holderDid,
            presentationRequestUrl = presentationRequestUrl,
            selectCredentialsForQuery = selectOnce,
            holderPoliciesToRun = holderPoliciesToRun,
            runPolicies = runPolicies,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
            resolvedAuthorizationRequest = resolvedAuthorizationRequest,
            holderCrypto2Key = null,
            clientIdTrustConfiguration = clientIdTrustConfiguration,
            beforeCredentialsUsed = beforeCredentialsUsed,
            expectedRequestObjectAudience = expectedRequestObjectAudience,
            unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
            credentialHolderKeyResolver = wallet.credentialHolderKeyResolver(isolatedCredentialsById),
            scaAuthorizer = null,
        )
    }

    private fun Wallet.credentialHolderKeyResolver(
        isolatedCredentialsById: Map<String, StoredCredential> = emptyMap(),
        scaAuthorizer: WalletScaPresentationAuthorizer? = null,
    ): CredentialPresentationKeyResolver = CredentialPresentationKeyResolver { credentialId, credential ->
        val stored = isolatedCredentialsById[credentialId] ?: findCredential(credentialId)
        if (stored?.holderKeyBinding == null && WalletPresentationFormatRegistry.resolve(credential.format) !=
            WalletPresentationFormatRegistry.SupportedFormat.MSO_MDOC) null else {
            val key = (stored?.let { resolveHolderKey(it, setOf(KeyUsage.SIGN)) }
                ?: resolveHolderKey(credentialId, setOf(KeyUsage.SIGN))).keyMaterial.requireCrypto2SigningKey()
            CredentialPresentationKey(key, credential.subject,
                scaAuthorizer?.let { authorizer ->
                    authorizer.forKey(key)
                })
        }
    }

    private fun Wallet.mdocHolderKeyResolver(): suspend (String, DigitalCredential) -> Crypto2Key = { credentialId, _ ->
        resolveHolderKey(credentialId, setOf(KeyUsage.SIGN)).keyMaterial.requireCrypto2SigningKey()
    }

    suspend fun submitDcApiPresentation(
        wallet: Wallet,
        request: SubmitDcApiPresentationRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
    ): DcApiCredentialResponse = submitDcApiPresentation(
        wallet = wallet,
        request = request,
        onEvent = onEvent,
        transactionDataTypeRegistry = transactionDataTypeRegistry,
        scaAuthorizer = null,
    )

    suspend fun submitDcApiPresentation(
        wallet: Wallet,
        request: SubmitDcApiPresentationRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        transactionDataTypeRegistry: TransactionDataTypeRegistry,
        scaAuthorizer: WalletScaPresentationAuthorizer?,
        paymentConsentPolicy: PaymentConsentPolicy? = null,
    ): DcApiCredentialResponse {
        request.selectedCredentialOptions.requireValidPresentationCredentialSelection()
        return previewedDcApiRequests.usePreparedRetainingOnFailure(wallet.id, request.requestId) { previewedRequest ->
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

            val selected = selectReviewedCredentials(wallet, requireNotNull(authorizationRequest.dcqlQuery),
                request.selectedCredentialOptions, request.selectedDisclosureOptions)
            paymentConsentPolicy?.let { policy ->
                previewedRequest.paymentConsent.confirm(request.paymentConsentRevision, authorizationRequest,
                    selected.matches, request.selectedCredentialOptions, request.selectedDisclosureOptions, keyMaterial, policy, holderDid = did)
            }
            val selectCredentialsForQuery: suspend (DcqlQuery) -> Map<String, List<DcqlMatcher.DcqlMatchResult>> = {
                onEvent(WalletSessionEvent.presentation_credentials_selected)
                selected.matches
            }

            WalletPresentFunctionality2.walletPresentDcApiHandling(
                holderKey = holderKey,
                holderDid = did,
                request = resolvedRequest,
                selectCredentialsForQuery = selectCredentialsForQuery,
                transactionDataTypeRegistry = transactionDataTypeRegistry,
                scaAuthorizer = scaAuthorizer?.let { authorizer ->
                    authorizer.forKey(holderKey)
                },
                credentialHolderKeyResolver = wallet.credentialHolderKeyResolver(scaAuthorizer = scaAuthorizer),
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
    suspend fun resolveRequestWithTrust(
        wallet: Wallet,
        request: ResolveVpRequestRequest,
        clientIdTrustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
        expectedRequestObjectAudience: String = AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE,
        unsignedRequestObjectPolicy: AuthorizationRequestResolver.UnsignedRequestObjectPolicy =
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
    ): ResolveVpRequestResult {
        val keyMaterial = wallet.resolveKeyMaterial(null, setOf(KeyUsage.SIGN))
            ?: error("No key available: wallet has no keyStores and no staticKey")
        val authRequest = WalletPresentFunctionality2.resolveAuthorizationRequest(
            presentationRequestUrl = request.requestUrl,
            unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
            legacyFallbackCallback = null,
            clientIdTrustConfiguration = clientIdTrustConfiguration,
            expectedRequestObjectAudience = expectedRequestObjectAudience,
            requestUriPostWalletMetadata = AuthorizationRequestResolver.buildRequestUriPostWalletMetadata(
                vpFormatsSupported = WalletPresentationFormatRegistry.buildVpFormatsSupported(
                    keyMaterial.presentationCapabilities(),
                ),
                trustConfiguration = clientIdTrustConfiguration,
                unsignedRequestObjectPolicy = unsignedRequestObjectPolicy,
            ),
        )

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
        val candidates = request.credentials.mapIndexedNotNull { idx, stored ->
            stored.toDcqlCandidate(idx.toString(), request.dcqlQuery)
        }
        val matched = DcqlMatcher.findMatches(request.dcqlQuery, candidates.map { it.credential }, candidates.matchingContext()).getOrThrow()
        return buildMatchResult(matched, idByIndex)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

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
        val storedById = wallet.streamAllCredentials().toList().associateBy { it.id }
        if (storedById.isEmpty()) return MatchCredentialsResult(emptyList(), 0, emptyMap())

        val holderKeyEligibility = selectHolderKeyEligibleFromSnapshot(
            wallet = wallet,
            query = request.dcqlQuery,
            storedById = storedById,
        )
        val matched = holderKeyEligibility.matched
        val availableCredentialQueryIds = matched.filterValues { it.isNotEmpty() }.keys
        val requirements = request.dcqlQuery.requiredCredentialRequirements()
        if (!requirements.satisfiedBy(availableCredentialQueryIds)) {
            holderKeyEligibility.bindingFailureForUnsatisfiedRequirements(
                requirements = requirements,
                availableCredentialQueryIds = availableCredentialQueryIds,
            )?.let { throw it }
        }
        return buildMatchResult(matched, emptyMap())
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
        expectedRequestObjectAudience: String = AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE,
        unsignedRequestObjectPolicy: AuthorizationRequestResolver.UnsignedRequestObjectPolicy =
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
        resolveAuthorizationRequest: suspend (Url) -> ResolvedAuthorizationRequest = { requestUrl ->
            this@WalletPresentationHandler.resolveAuthorizationRequest(
                { WalletPresentationFormatRegistry.defaultCapabilities() },
                requestUrl,
                clientIdTrustConfiguration,
                expectedRequestObjectAudience,
                unsignedRequestObjectPolicy,
            )
        },
    ): BuildVpTokenResult = buildVpToken(
        wallet, request, scaAuthorizer = null, transactionDataTypeRegistry,
        clientIdTrustConfiguration, expectedRequestObjectAudience, unsignedRequestObjectPolicy,
        resolveAuthorizationRequest,
    )

    /** Builds an isolated proof using trusted, application-owned SCA authorization. */
    suspend fun buildVpToken(
        wallet: Wallet,
        request: BuildVpTokenRequest,
        scaAuthorizer: WalletScaPresentationAuthorizer?,
        transactionDataTypeRegistry: TransactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
        clientIdTrustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
        expectedRequestObjectAudience: String = AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE,
        unsignedRequestObjectPolicy: AuthorizationRequestResolver.UnsignedRequestObjectPolicy =
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
        resolveAuthorizationRequest: suspend (Url) -> ResolvedAuthorizationRequest = { requestUrl ->
            this@WalletPresentationHandler.resolveAuthorizationRequest(
                { WalletPresentationFormatRegistry.defaultCapabilities() },
                requestUrl,
                clientIdTrustConfiguration,
                expectedRequestObjectAudience,
                unsignedRequestObjectPolicy,
            )
        },
    ): BuildVpTokenResult = buildVpToken(
        wallet, request, scaAuthorizer, holderPoliciesToRun = null, transactionDataTypeRegistry,
        clientIdTrustConfiguration, expectedRequestObjectAudience, unsignedRequestObjectPolicy,
        resolveAuthorizationRequest,
    )

    /** When supplied, [holderPoliciesToRun] must allow the selected credentials before signing. */
    suspend fun buildVpToken(
        wallet: Wallet,
        request: BuildVpTokenRequest,
        scaAuthorizer: WalletScaPresentationAuthorizer?,
        holderPoliciesToRun: Flow<HolderPolicy>?,
        transactionDataTypeRegistry: TransactionDataTypeRegistry = TransactionDataTypeRegistry(emptySet()),
        clientIdTrustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
        expectedRequestObjectAudience: String = AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE,
        unsignedRequestObjectPolicy: AuthorizationRequestResolver.UnsignedRequestObjectPolicy =
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
        resolveAuthorizationRequest: suspend (Url) -> ResolvedAuthorizationRequest = { requestUrl ->
            this@WalletPresentationHandler.resolveAuthorizationRequest(
                { WalletPresentationFormatRegistry.defaultCapabilities() },
                requestUrl,
                clientIdTrustConfiguration,
                expectedRequestObjectAudience,
                unsignedRequestObjectPolicy,
            )
        },
    ): BuildVpTokenResult {
        val keyMaterial = request.key?.key?.let { WalletKeyStoreEntry(it.getKeyId(), it, null) }
            ?: wallet.resolveKeyMaterial(request.keyId, setOf(KeyUsage.SIGN))
            ?: throw IllegalArgumentException("Wallet has no key available for VP token building")
        val (resolvedAuthorizationRequest, validatedTransactionData) = resolveAndValidatePresentationRequest(
            requestUrl = request.requestUrl,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
            formatCapabilities = { keyMaterial.presentationCapabilities() },
            resolveAuthorizationRequest = resolveAuthorizationRequest,
        )
        val authorizationRequest = resolvedAuthorizationRequest.authorizationRequest
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

        val matched = selectFromStores(
            wallet = wallet,
            query = dcqlQuery,
            useWalletCredentialIds = true,
            eligibleCredentialIds = selectedCredentialOptions.mapTo(mutableSetOf()) { it.credentialId },
        )
        val missingSelectedQueries = selectedCredentialOptions
            .filter { option ->
                matched[option.queryId].orEmpty().none { result -> result.credential.id == option.credentialId }
            }
            .map { it.queryId }
            .distinct()
        if (missingSelectedQueries.isNotEmpty()) {
            throw RequiredCredentialUnavailableException(
                missingSelectedQueries,
                "No matches found for required credential queries: $missingSelectedQueries",
            )
        }
        val selected = matched.selectCredentialOptions(
            selectedCredentialOptions = selectedCredentialOptions,
            selectedDisclosureOptions = request.selectedDisclosureOptions,
        )
        require(requirements.satisfiedBy(selected.keys)) {
            "Selected credential option(s) do not match required presentation credential query constraints"
        }

        if (holderPoliciesToRun != null) {
            WalletPresentFunctionality2.requireHolderPoliciesAllow(holderPoliciesToRun, selected, validatedTransactionData)
        }

        val crypto2Key = keyMaterial.crypto2Key
        val vpToken = if (crypto2Key != null) {
            WalletPresentFunctionality2.buildVpToken(
                authorizationRequest = authorizationRequest,
                matchedCredentials = selected,
                holderKey = crypto2Key,
                holderDid = did,
                transactionDataTypeRegistry = transactionDataTypeRegistry,
                clientMetadata = resolvedAuthorizationRequest.effectiveClientMetadata,
                credentialHolderKeyResolver = wallet.credentialHolderKeyResolver(scaAuthorizer = scaAuthorizer),
                scaAuthorizer = scaAuthorizer?.forKey(crypto2Key),
            )
        } else {
            WalletPresentFunctionality2.buildVpToken(
                authorizationRequest = authorizationRequest,
                matchedCredentials = selected,
                holderKey = requireNotNull(keyMaterial.legacyKey) {
                    "Key '${keyMaterial.keyId}' has no usable signing representation"
                },
                holderDid = did,
                transactionDataTypeRegistry = transactionDataTypeRegistry,
                holderCrypto2Key = null,
                clientMetadata = resolvedAuthorizationRequest.effectiveClientMetadata,
                credentialHolderKeyResolver = wallet.credentialHolderKeyResolver(scaAuthorizer = scaAuthorizer),
                scaAuthorizer = null,
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
        expectedRequestObjectAudience: String = AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE,
        unsignedRequestObjectPolicy: AuthorizationRequestResolver.UnsignedRequestObjectPolicy =
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
        resolveAuthorizationRequest: suspend (Url) -> ResolvedAuthorizationRequest = { requestUrl ->
            this@WalletPresentationHandler.resolveAuthorizationRequest(
                { WalletPresentationFormatRegistry.defaultCapabilities() },
                requestUrl,
                clientIdTrustConfiguration,
                expectedRequestObjectAudience,
                unsignedRequestObjectPolicy,
            )
        },
    ): WalletPresentResult {
        val resolvedAuthorizationRequest = resolveAndValidatePresentationRequest(
            requestUrl = request.requestUrl,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
            resolveAuthorizationRequest = resolveAuthorizationRequest,
        ).resolved
        return WalletPresentFunctionality2.sendAuthorizationResponse(
            authorizationRequest = resolvedAuthorizationRequest.authorizationRequest,
            vpToken = request.vpToken,
            idToken = request.idToken,
            clientMetadata = resolvedAuthorizationRequest.effectiveClientMetadata,
        ).getOrThrow()
    }

    /**
     * @param capabilities resolved lazily: wallet metadata is only advertised when a `request_uri` is
     *   actually fetched, so a request carrying its object inline needs no signing key to be resolved.
     */
    private suspend fun resolveAuthorizationRequest(
        capabilities: () -> WalletPresentationFormatRegistry.RuntimeCapabilities,
        requestUrl: Url,
        clientIdTrustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
        expectedRequestObjectAudience: String = AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE,
        unsignedRequestObjectPolicy: AuthorizationRequestResolver.UnsignedRequestObjectPolicy =
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.ALLOW_UNSIGNED,
    ): ResolvedAuthorizationRequest {
        val haipRedirect = requestUrl.protocol.name.equals("haip-vp", ignoreCase = true)
        val requestPolicy = if (haipRedirect) {
            require(!requestUrl.parameters["request_uri"].isNullOrBlank()) {
                "haip-vp requires a signed Request Object by reference (request_uri)"
            }
            AuthorizationRequestResolver.UnsignedRequestObjectPolicy.REQUIRE_SIGNED
        } else unsignedRequestObjectPolicy
        val fetcher = WebDataFetcher(WebDataFetcherId.OPENID4VP_WALLET_RESOLVE_AUTHORIZATIONREQUEST)
        val resolved = AuthorizationRequestResolver.resolve(
            requestUrl = requestUrl,
            unsignedRequestObjectPolicy = requestPolicy,
            trustConfiguration = clientIdTrustConfiguration,
            expectedRequestObjectAudience = expectedRequestObjectAudience,
            fetchRequestUri = { requestUri, requestUriMethod ->
                AuthorizationRequestResolver.fetchRequestUriWithWebDataFetcher(
                    webResolveAuthReq = fetcher,
                    requestUri = requestUri,
                    unsignedRequestObjectPolicy = requestPolicy,
                    requestUriMethod = requestUriMethod,
                    expectedRequestObjectAudience = expectedRequestObjectAudience,
                    requestUriPostWalletMetadata = AuthorizationRequestResolver.buildRequestUriPostWalletMetadata(
                        WalletPresentationFormatRegistry.buildVpFormatsSupported(capabilities()),
                        clientIdTrustConfiguration,
                        requestPolicy,
                    ),
                )
            },
        )
        if (haipRedirect) {
            require(resolved.authorizationRequest.responseMode == OpenID4VPResponseMode.DIRECT_POST_JWT) {
                "haip-vp requires encrypted direct_post.jwt responses"
            }
        }
        return resolved
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
    ): ValidatedPresentationRequest {
        val resolvedAuthorizationRequest = resolveAuthorizationRequest(requestUrl)
        val validation = PresentationRequestValidator.validate(
            resolvedRequest = resolvedAuthorizationRequest,
            transactionDataTypeRegistry = transactionDataTypeRegistry,
            formatCapabilities = formatCapabilities,
        )
        if (validation is PresentationRequestValidationResult.Invalid) {
            if (validation.error.code == WalletPresentFunctionality2.OID4VPErrorCode.INVALID_TRANSACTION_DATA) {
                throw InvalidPresentationTransactionDataException(validation.error.message)
            }
            error(
                "Presentation request is invalid (${validation.error.code.code}): ${validation.error.message}"
            )
        }
        return ValidatedPresentationRequest(
            resolvedAuthorizationRequest,
            (validation as PresentationRequestValidationResult.Valid).transactionData,
        )
    }

    private data class ValidatedPresentationRequest(
        val resolved: ResolvedAuthorizationRequest,
        val transactionData: List<DecodedTransactionData>,
    )

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

    /** Discards only a preview issued by this exact open wallet instance. */
    suspend fun discardInstancePreview(wallet: Wallet, handle: PresentationPreviewHandle) {
        previewedAuthorizationRequests.discard(wallet.id, handle.value) { require(it.owner === wallet) { "Preview belongs to another wallet instance" } }
    }

    /** Releases the previews belonging to one closing wallet instance. */
    suspend fun closeInstancePreviews(wallet: Wallet) {
        previewedAuthorizationRequests.clearInstance(wallet.id) { it.owner === wallet }
        previewedDcApiRequests.clearInstance(wallet.id) { it.owner === wallet }
    }

    suspend fun clearPreviews(wallet: Wallet) {
        previewedAuthorizationRequests.clearWallet(wallet.id)
        previewedDcApiRequests.clearWallet(wallet.id)
    }

    private suspend fun rememberPreviewedDcApiRequest(
        wallet: Wallet,
        request: ResolvedDcApiRequest,
        allowedCredentialIds: Set<String>,
    ): String = previewedDcApiRequests.create(wallet.id, PreviewedDcApiRequest(wallet, request, allowedCredentialIds))
}

internal fun BuildVpTokenRequest.resolveSelectedCredentialOptions(): List<PresentationCredentialSelection> =
    resolveSelectedCredentialOptions(selectedCredentialOptions, selectedCredentialIds)

/** Shared selection precedence for construction, status review and usage accounting. */
fun resolveSelectedCredentialOptions(
    selectedCredentialOptions: List<PresentationCredentialSelection>,
    selectedCredentialIds: Map<String, List<String>>,
): List<PresentationCredentialSelection> {
    if (selectedCredentialOptions.isNotEmpty()) return selectedCredentialOptions
    return selectedCredentialIds.flatMap { (queryId, credentialIds) ->
        credentialIds.map { credentialId ->
            PresentationCredentialSelection(queryId = queryId, credentialId = credentialId)
        }
    }
}

private suspend fun resolvePreviewKeyMaterial(
    wallet: Wallet,
    request: PreviewPresentationRequest,
): WalletKeyStoreEntry? =
    request.key?.key?.let { WalletKeyStoreEntry(it.getKeyId(), it, null) }
        ?: wallet.resolveKeyMaterial(request.keyId, setOf(KeyUsage.SIGN))
