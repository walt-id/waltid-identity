@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.wallet2.issuance.CredentialReceiptTrust
import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.crypto2.keys.KeyUseAuthorizationSupport
import id.walt.mdoc.proximity.mobile.BleProximityTransportFactory
import id.walt.mdoc.proximity.mobile.NfcHostPlatformAdapter
import id.walt.mdoc.proximity.mobile.WifiAwareProximityTransportFactory
import id.walt.mdoc.readertrust.MdocReaderAuthenticationTrustEvaluator
import id.walt.mdoc.readertrust.ReaderTrustEvaluator
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.openid4vp.clientidprefix.prefixes.Unsupported
import id.walt.wallet2.consent.PaymentConsentPolicy
import id.walt.wallet2.consent.PaymentConsentResolver
import id.walt.wallet2.consent.PaymentCredentialIssuer
import id.walt.wallet2.consent.PreparedPaymentConsent
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.handlers.KeyAttestationProvider
import id.walt.wallet2.handlers.KeyAttestationProviderResolver
import id.walt.wallet2.handlers.PresentationPreviewHandle
import id.walt.wallet2.handlers.PreviewDcApiPresentationRequest
import id.walt.wallet2.handlers.PreviewPresentationRequest
import id.walt.wallet2.handlers.RejectPresentationRequest
import id.walt.wallet2.handlers.SubmitDcApiPresentationRequest
import id.walt.wallet2.handlers.SubmitPresentationRequest
import id.walt.wallet2.handlers.WalletDeferredIssuanceReference
import id.walt.wallet2.handlers.WalletIssuanceAuthorization
import id.walt.wallet2.handlers.WalletIssuanceAuthorizationCallback
import id.walt.wallet2.handlers.WalletIssuanceContinuation
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.handlers.WalletIssuanceSessionService
import id.walt.wallet2.handlers.WalletIssuanceSessionState
import id.walt.wallet2.handlers.WalletIssuanceSessionStore
import id.walt.wallet2.handlers.WalletPresentationHandler
import id.walt.wallet2.handlers.WalletScaPresentationAuthorizer
import id.walt.wallet2.mobile.credentials.MobileWalletCredential
import id.walt.wallet2.mobile.credentials.toMobileWalletCredential
import id.walt.wallet2.mobile.credentials.MobileWalletCredentialRead
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletAnnexCEngine
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletAnnexCParsedRequest
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletAnnexCPreview
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletAnnexCRequest
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletAnnexCSubmission
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletCredentialRegistrationResult
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletCredentialRegistry
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletDigitalCredentialCapabilities
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletDigitalCredentialPreview
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletDigitalCredentialProtocols
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletDigitalCredentialRequest
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletDigitalCredentialRequestInfo
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletDigitalCredentialResponse
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletRegistration
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletRegistryProjection
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletStaleRegistryEntryException
import id.walt.wallet2.mobile.digitalcredentials.UnavailableMobileWalletCredentialRegistry
import id.walt.wallet2.mobile.digitalcredentials.registryRecords
import id.walt.wallet2.mobile.identity.MobileWalletKeyType
import id.walt.wallet2.mobile.identity.SigningIdentityManager
import id.walt.wallet2.mobile.issuance.IssuanceClientRegistration
import id.walt.wallet2.mobile.issuance.MobileWalletAuthorizationId
import id.walt.wallet2.mobile.issuance.MobileWalletIssuanceSession
import id.walt.wallet2.mobile.issuance.MobileWalletIssuanceSessionId
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
import id.walt.wallet2.mobile.issuance.MobileWalletIssuanceKeys
import id.walt.wallet2.mobile.issuance.MobileWalletCredentialHolders
import id.walt.wallet2.mobile.issuance.MobileWalletCredentialSelection
import id.walt.wallet2.mobile.issuance.MobileWalletHolderBinding
import id.walt.wallet2.mobile.issuance.MobileWalletIssuanceRequest
import id.walt.wallet2.mobile.issuance.MobileWalletIssuanceProviders
import id.walt.wallet2.mobile.issuance.WalletAttestationConfig
import id.walt.wallet2.mobile.issuance.WalletProviderConfig
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationReview
import id.walt.wallet2.mobile.presentation.MobileWalletPreparedPresentationReview
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationErrorCode
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationPreviewHandle
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationPreviewResult
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationResult
import id.walt.wallet2.mobile.presentation.MobileWalletPresentationSelection
import id.walt.wallet2.mobile.presentation.MobileWalletTransactionDataProfile
import id.walt.wallet2.mobile.presentation.encodeJsonObject
import id.walt.wallet2.mobile.presentation.toMobileDigitalCredentialResponse
import id.walt.wallet2.mobile.presentation.toMobilePresentationResult
import id.walt.wallet2.mobile.presentation.toMobilePreview
import id.walt.wallet2.mobile.presentation.toMobileReview
import id.walt.wallet2.mobile.presentation.toTransactionDataTypeRegistry
import id.walt.wallet2.mobile.proximity.ProximityCapabilities
import id.walt.wallet2.mobile.proximity.ProximityConfiguration
import id.walt.wallet2.mobile.proximity.ProximityCoordinator
import id.walt.wallet2.mobile.proximity.ProximitySession
import id.walt.wallet2.mobile.proximity.snapshot
import id.walt.wallet2.persistence.keys.MobileWalletKeyStore
import id.walt.wallet2.status.CredentialStatusRefreshMode
import id.walt.wallet2.status.CredentialStatusRefreshResult
import id.walt.wallet2.status.WalletCredentialReference
import id.walt.wallet2.status.WalletCredentialStatusSnapshot
import id.walt.wallet2.status.reviewGuard
import id.waltid.openid4vci.wallet.attestation.WalletAttestationProvider
import id.waltid.openid4vci.wallet.metadata.CredentialIssuerMetadataTrustResolver
import id.waltid.openid4vp.wallet.DcApiRequestProtocol
import id.waltid.openid4vp.wallet.DcApiWallet
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Android and iOS facade for the walt.id wallet SDK.
 *
 * Use [MobileWalletFactory] to create instances with the correct platform storage, key provider,
 * and SQLDelight driver. The facade keeps the public mobile API intentionally small while delegating
 * protocol work to the core wallet handlers.
 */
public class MobileWallet internal constructor(
    walletId: String,
    private val keyStore: MobileWalletKeyStore,
    private val didStore: WalletDidStore,
    private val credentialStore: WalletCredentialStore,
    private val issuanceSessionStore: WalletIssuanceSessionStore? = null,
    private val runKeyUseAuthorizationPreflight: suspend (MobileWalletKeyType, KeyUseAuthorizationPolicy) -> KeyUseAuthorizationSupport =
        { _, _ -> error("This MobileWallet does not support key-use authorization preflight") },
    private val defaultKeyUseAuthorizationPolicy: KeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
    private val generateAndPersistHolderKey: suspend (MobileWalletKeyType, KeyUseAuthorizationPolicy) -> id.walt.crypto2.keys.Key =
        { _, _ -> error("Holder key creation requires the persistent mobile wallet factory") },
    attestationConfig: WalletAttestationConfig? = null,
    attestationProvider: WalletAttestationProvider? = null,
    walletProviderConfig: WalletProviderConfig? = null,
    private val preferredLocales: List<String> = emptyList(),
    receiptTrust: CredentialReceiptTrust? = null,
    private val transactionDataProfiles: List<MobileWalletTransactionDataProfile> = emptyList(),
    private val clientIdTrustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
    private val credentialIssuerMetadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
    private val credentialRegistry: MobileWalletCredentialRegistry = UnavailableMobileWalletCredentialRegistry,
    registrationProjection: MobileWalletRegistryProjection = MobileWalletRegistryProjection.Full,
    private val readerTrustEvaluator: ReaderTrustEvaluator = MdocReaderAuthenticationTrustEvaluator,
    private val onEvent: suspend (MobileWalletEvent) -> Unit = {},
    onDigitalCredentialRegistryChanged: suspend () -> Unit = {},
    private val persistenceOwner: MobileWalletPersistenceOwner? = null,
    private val deleteLocalPersistence: suspend () -> Unit = {},
    private val statusMaintenance: StatusMaintenanceEnrollmentStore? = null,
    private val managedCredentialStatus: Boolean = false,
    private val proximityTransportFactory: BleProximityTransportFactory? = null,
    private val proximityNfcHostPlatformAdapter: NfcHostPlatformAdapter? = null,
    private val proximityWifiAwareTransportFactory: WifiAwareProximityTransportFactory? = null,
    /**
     * Issuance transport override. The configured iOS engine disables Foundation response caching.
     * A supplied client must also prevent automatic caching of sensitive issuance responses.
     */
    private val issuanceHttpClient: HttpClient? = null,
    private val scaAuthorizer: WalletScaPresentationAuthorizer? = null,
    paymentCredentialIssuers: List<PaymentCredentialIssuer> = emptyList(),
    paymentMetadataHttpClient: HttpClient? = null,
    createSigningIdentityManager: ((MobileWalletLifecycle, suspend () -> Unit) -> SigningIdentityManager)? = null,
) {
    /**
     * Buffered stream of recent issuance and presentation events emitted by this wallet.
     */
    public val events: Flow<MobileWalletEvent>
        get() = lifecycle.observe(eventStream.events)

    /** Signing identity creation, backup and restoration; recovery integrations are explicitly configured. */
    public val signingIdentity: SigningIdentityManager
        get() = checkNotNull(signingIdentityManager) { "Identity lifecycle requires the persistent mobile wallet factory" }

    /** Initial and conflated status-only snapshots; observation does not retain network work. */
    public val credentialStatuses: Flow<WalletCredentialStatusSnapshot>
        get() = lifecycle.observe(statusUpdates)

    /**
     * Last result of replacing the platform's presentation records, or null before the first attempt.
     * Credential storage commits first. Retry [refreshDigitalCredentialRegistration] to rebuild the projection.
     * Creation-option registration and the host notification run independently. This StateFlow retains its
     * last value after close. The host owns and cancels its collection.
     */
    public val digitalCredentialRegistration: StateFlow<MobileWalletCredentialRegistrationResult?>
        get() = registration.state

    /**
     * Attaches the provider used when an issuer requires key attestation in a credential proof.
     * This runtime dependency must be reattached whenever the wallet is recreated.
     */
    public fun attachKeyAttestationProvider(provider: KeyAttestationProvider): MobileWallet = apply {
        wallet.attachKeyAttestationProvider(provider)
    }

    /** Attaches issuer-specific key attestation support. Supply it again after wallet recreation. */
    public fun attachKeyAttestationProviderResolver(
        resolver: KeyAttestationProviderResolver,
    ): MobileWallet = apply {
        val fallback = issuanceProviders.walletProviderClient?.takeIf {
            issuanceProviders.walletProviderComposition != WalletProviderConfig.Composition.WalletInstance
        }
        wallet.attachKeyAttestationProviderResolver(
            if (fallback == null) resolver else KeyAttestationProviderResolver { issuer ->
                resolver.resolve(issuer) ?: fallback
            },
        )
    }

    /**
     * Checks the exact BLE roles selected by [configuration] without creating keys, UUIDs, listeners,
     * scanners, or advertisers.
     */
    public suspend fun proximityPresentationCapabilities(
        configuration: ProximityConfiguration = ProximityConfiguration(),
    ): ProximityCapabilities = lifecycle.use {
        proximityCoordinator.capabilities(configuration)
    }

    /**
     * Starts one single-use in-person presentation session.
     *
     * Only one proximity session may be active for this wallet. A second start fails before any
     * transaction material or radio resource is created.
     */
    public suspend fun startProximityPresentation(
        configuration: ProximityConfiguration = ProximityConfiguration(),
    ): ProximitySession = lifecycle.use {
        proximityCoordinator.start(configuration)
    }

    /**
     * Explicitly creates holder keys for a requested issuance batch using the platform key policy.
     * The returned keys remain wallet-owned. For keys scoped to acceptance, use
     * [MobileWalletCredentialHolders.NewKeys]; preview never generates keys.
     */
    public suspend fun createIssuanceHolderKeys(
        count: Int,
        keyType: MobileWalletKeyType? = null,
        didMethod: String = "key",
        keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy? = null,
    ): List<MobileWalletHolderBinding> = lifecycle.use {
        issuanceKeys.create(count, keyType, didMethod, keyUseAuthorizationPolicy)
    }

    /** Checks whether a key-use authorization request is supported without creating or persisting a key. */
    public suspend fun keyUseAuthorizationPreflight(
        keyType: MobileWalletKeyType = MobileWalletKeyType.secp256r1,
        keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy = defaultKeyUseAuthorizationPolicy,
    ): KeyUseAuthorizationSupport = lifecycle.use {
        runKeyUseAuthorizationPreflight(keyType, keyUseAuthorizationPolicy)
    }

    /**
     * Resolves an offer and starts a bound OpenID4VCI 1.0 issuance session.
     *
     * Accept the returned grant after showing its credential and issuer preview.
     */
    public suspend fun startIssuance(request: MobileWalletIssuanceRequest): MobileWalletIssuanceSession = lifecycle.use {
        MobileWalletIssuanceSession.from(issuanceSessions.startOffer(issuanceKeys.offerRequest(request), preferredLocales), this)
    }

    /** Reopens an offer that is still awaiting acceptance, including after wallet recreation. */
    public suspend fun resumeIssuance(id: MobileWalletIssuanceSessionId): MobileWalletIssuanceSession? = lifecycle.use {
        issuanceSessions.reviewOffer(id.value)?.let { MobileWalletIssuanceSession.from(it, this) }
    }

    /**
     * Validates and consumes a browser callback bound to an authorization-code session.
     *
     * The callback target, OAuth state, authorization code, PKCE verifier, issuer metadata, and
     * selected holder key are all taken from or checked against the authoritative session record.
     */
    public suspend fun continueAuthorizationIssuance(
        sessionId: MobileWalletAuthorizationId,
        callbackUri: String,
    ): WalletIssuanceOutcome = lifecycle.use {
        issuanceSessions.continueAuthorization(
            WalletIssuanceAuthorizationCallback(sessionId = sessionId.value, callbackUri = callbackUri),
        ).alsoRefreshDigitalCredentialRegistration()
    }

    /** Cancels an active issuance session and discards its protocol continuation material. */
    public suspend fun cancelIssuance(sessionId: MobileWalletIssuanceSessionId): WalletIssuanceOutcome = lifecycle.use {
        issuanceSessions.cancel(sessionId.value)
    }

    /** Recovers retained deferred handles after an interrupted flow or wallet recreation. */
    public suspend fun listDeferredIssuance(): List<WalletIssuanceContinuation> = lifecycle.use {
        issuanceSessions.listIssuanceContinuations()
    }

    /** Polls a typed deferred credential result returned by a previous issuance continuation. */
    public suspend fun resumeDeferredIssuance(
        continuation: WalletDeferredIssuanceReference,
    ): WalletIssuanceOutcome = lifecycle.use {
        issuanceSessions.resumeDeferred(continuation.id).alsoRefreshDigitalCredentialRegistration()
    }

    /**
     * Lists all credentials currently stored in the mobile wallet.
     *
     * @return Credential entries, including display JSON, ordered by the underlying credential store.
     */
    public suspend fun credentials(): List<MobileWalletCredential> = lifecycle.use {
        wallet.streamAllCredentials().map { it.toMobileWalletCredential() }.toList()
    }

    /** Reads credentials with typed, local status metadata without rebuilding them for status updates. */
    public suspend fun credentialReads(): List<MobileWalletCredentialRead> = lifecycle.use {
        wallet.readCredentialsWithStatus().map { read ->
            MobileWalletCredentialRead(read.credential.toMobileWalletCredential(), read.status, read.evaluatedAt.toString())
        }
    }

    /** Explicit wallet-owned checks; omitted IDs select all stored credentials. */
    public suspend fun refreshCredentialStatuses(
        credentialIds: List<String>? = null,
        mode: CredentialStatusRefreshMode = CredentialStatusRefreshMode.IF_DUE,
    ): List<CredentialStatusRefreshResult> {
        val service = lifecycle.use { wallet.credentialStatuses() }
        require(credentialIds == null || credentialIds.isNotEmpty())
        return service.refresh(credentialIds?.mapTo(mutableSetOf()) { WalletCredentialReference(0, it) }, mode)
    }

    /** Current local status; no issuer request is made by this operation. */
    public suspend fun credentialStatusSnapshot(): WalletCredentialStatusSnapshot = lifecycle.use {
        wallet.credentialStatuses().snapshot()
    }

    /** Acknowledges only the notice version presented to the holder. */
    public suspend fun acknowledgeCredentialStatusNotice(credentialId: String, version: String): Unit = lifecycle.use {
        wallet.credentialStatuses().acknowledgeNotice(WalletCredentialReference(0, credentialId), version)
    }

    /** Makes the local status stream reread encrypted snapshots written by another handle. */
    public suspend fun invalidateCredentialStatusSnapshot(): Unit = lifecycle.use {
        wallet.notifyCredentialStatusInventoryChanged()
    }

    /** Reads the background opt-in for this managed wallet, or null for an unsupported store. */
    public suspend fun credentialStatusMaintenanceEnrollment(): CredentialStatusMaintenanceEnrollment? =
        lifecycle.use { statusMaintenance?.get() }

    /**
     * Reconciles maintenance with the host's automatic-check preference.
     * Disabling automatic checks retires the grant while preserving consent. Set [resumePaused]
     * only for an explicit re-enable. On startup a paused enrollment stays paused.
     */
    public suspend fun reconcileCredentialStatusMaintenance(
        automaticChecks: Boolean,
        resumePaused: Boolean = false,
    ): CredentialStatusMaintenanceEnrollment? = lifecycle.use { statusMaintenance?.reconcile(automaticChecks, resumePaused) }

    /** Updates the existing wallet's opt-in. The app calls this from its authorized foreground flow. */
    public suspend fun setCredentialStatusMaintenance(
        preference: CredentialStatusMaintenancePreference,
    ): CredentialStatusMaintenanceEnrollment? = lifecycle.use { statusMaintenance?.set(preference) }

    /** Closes this instance to new operations and cancels status work without deleting stored data. */
    public suspend fun close(): Unit = lifecycle.close {
        WalletPresentationHandler.closeInstancePreviews(wallet)
        wallet.closeCredentialStatusService()
        proximityCoordinator.shutdown()
        closePersistence()
        persistenceOwner?.storageLease?.close()
    }

    /** Returns the native adapter's current runtime capability snapshot. */
    public fun digitalCredentialCapabilities(): MobileWalletDigitalCredentialCapabilities =
        credentialRegistry.capabilities

    /**
     * Synchronizes platform credential metadata to a minimal view of the current wallet state.
     *
     * Raw credentials, issuer-signed payloads, and private keys are never registered, and neither
     * are SD-JWT VC infrastructure claims such as issuer, expiry, confirmation or status. The adapter
     * receives the remaining decoded claims and projects them into its platform index. The native
     * iOS adapter receives only mdoc identifiers and document types, without processing claims or
     * artwork. Custom adapters continue to receive full records. Android
     * retains compound and embedded media fields for presence matching without registering their
     * values; those fields cannot satisfy an exact-value constraint in the platform matcher.
     *
     * This is the retry entry point: it is safe to call at any time, and calling it again after a
     * failure re-publishes the current wallet state. It reports an adapter failure through the
     * returned result and [digitalCredentialRegistration], and only propagates an exception if
     * reading the wallet's own credentials failed, or the caller was cancelled. Refreshes on this
     * wallet serialize snapshot creation and publication; a queued retry reads the current store.
     */
    public suspend fun refreshDigitalCredentialRegistration(): MobileWalletCredentialRegistrationResult = lifecycle.use {
        registration.refresh()
    }


    /**
     * Removes one credential and re-publishes the native registry to reject stale selections.
     *
     * The removal is authoritative: the returned value reports whether the credential store removed
     * the credential, regardless of whether the platform registry could be updated afterwards.
     */
    public suspend fun deleteCredential(credentialId: String): Boolean = lifecycle.use {
        val removed = credentialStore.removeCredential(credentialId)
        wallet.notifyCredentialStatusInventoryChanged()
        registration.afterMutation()
        return@use removed
    }

    /**
     * Resolves an OS-mediated OpenID4VP request and returns consent metadata without releasing a credential.
     * The caller-provided origin must already have been asserted by the platform adapter. For an
     * unsigned request, the returned [MobileWalletDigitalCredentialPreview.verifiedOrigin] is the
     * authenticated requester identity and [MobileWalletDigitalCredentialRequestInfo.clientId]
     * remains null because the untrusted request-supplied `client_id` is ignored. For a signed
     * request, [MobileWalletDigitalCredentialRequestInfo.clientId] is the authenticated Request
     * Object client identifier after signature verification against [clientIdTrustConfiguration].
     * Unsigned (`openid4vp-v1-unsigned`) and signed (`openid4vp-v1-signed`) Digital Credentials
     * requests are accepted. Multisigned is not.
     */
    public suspend fun previewDigitalCredentialPresentation(
        request: MobileWalletDigitalCredentialRequest,
    ): MobileWalletDigitalCredentialPreview = lifecycle.use {
        require(request.protocol != MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C) {
            "ISO 18013-7 Annex C requests use the dedicated Annex C facade"
        }
        val currentRecords = wallet.registryRecords(preferredLocales)
        val credentialIdsByRegistryId = currentRecords.associate { it.registryEntryId to it.credentialId }
        val selectedCredentialIds = request.selectedRegistryEntryIds.map { registryId ->
            credentialIdsByRegistryId[registryId] ?: throw MobileWalletStaleRegistryEntryException(registryId)
        }.toSet()

        val result = WalletPresentationHandler.previewDcApiPresentation(
            wallet = wallet,
            request = PreviewDcApiPresentationRequest(
                protocol = request.protocol,
                data = Json.parseToJsonElement(request.dataJson).jsonObject,
                origin = request.verifiedOrigin,
                // Empty means the platform supplied no credential restriction, and DCQL then matches
                // over the whole store. Platform adapters must fail closed when a selection was
                // expected but could not be resolved, because arriving here from a malformed
                // selection would silently widen the candidate set.
                eligibleCredentialIds = selectedCredentialIds.ifEmpty { null },
            ),
            transactionDataTypeRegistry = transactionDataProfiles.toTransactionDataTypeRegistry(),
            clientIdTrustConfiguration = clientIdTrustConfiguration,
            onEvent = ::emitSessionEvent,
        )
        result.toMobilePreview(request.protocol, preferredLocales, transactionDataProfiles)
    }

    /** Resolves payment instructions for the chosen DC API credentials without signing. */
    public suspend fun prepareDigitalCredentialPaymentConsent(
        requestId: String,
        selection: MobileWalletPresentationSelection,
        did: String? = null,
    ): PreparedPaymentConsent? = lifecycle.use {
        WalletPresentationHandler.prepareDcApiPaymentConsent(
            wallet, SubmitDcApiPresentationRequest(requestId,
                selection.coreCredentials(),
                selection.coreDisclosures(), did = did),
            paymentConsentPolicy,
        )
    }

    /** Cancels a retained DC API review, including an in-flight native authorization. */
    public suspend fun discardDigitalCredentialPreview(requestId: String): Unit = lifecycle.use {
        WalletPresentationHandler.discardDcApiPreview(wallet, requestId)
    }


    /** Provider reviews use cached observations; this method never starts a status request. */
    public suspend fun submitStatusReviewedDigitalCredentialPresentation(
        requestId: String,
        selection: MobileWalletPresentationSelection,
        reviewedStatus: Map<String, String>,
        did: String? = null, paymentConsentRevision: String? = null,
    ): MobileWalletDigitalCredentialResponse {
        val revisions = reviewedStatus.toMap()
        val guard = lifecycle.use { wallet.credentialStatuses().reviewGuard(selection.credentials.mapTo(mutableSetOf()) { it.credentialId }, revisions) }
        guard.check()
        return withContext(guard) {
            submitDigitalCredentialPresentation(requestId, selection, did, paymentConsentRevision)
        }
    }

    /**
     * Builds a response for a retained Digital Credentials preview. No network transport is performed.
     * Without [paymentConsentRevision], the host is responsible for platform-confirmed payment consent.
     * A supplied revision is checked against the prepared app review. Native SCA authorization applies
     * to both paths.
     */
    public suspend fun submitDigitalCredentialPresentation(
        requestId: String,
        selection: MobileWalletPresentationSelection,
        did: String? = null,
        paymentConsentRevision: String? = null,
    ): MobileWalletDigitalCredentialResponse = lifecycle.use {
        require(selection.credentials.isNotEmpty()) { "At least one credential must be selected after consent" }
        val response = WalletPresentationHandler.submitDcApiPresentation(
            wallet = wallet,
            request = SubmitDcApiPresentationRequest(
                requestId = requestId,
                selectedCredentialOptions = selection.coreCredentials(),
                selectedDisclosureOptions = selection.coreDisclosures(),
                did = did,
                paymentConsentRevision = paymentConsentRevision,
            ),
            transactionDataTypeRegistry = transactionDataProfiles.toTransactionDataTypeRegistry(),
            onEvent = ::emitSessionEvent,
            scaAuthorizer = scaAuthorizer,
            paymentConsentPolicy = paymentConsentPolicy.takeIf { paymentConsentRevision != null },
        )
        return@use response.toMobileDigitalCredentialResponse()
    }

    /** Builds the single-field OpenID4VP DC API error object required by Appendix A. */
    public fun digitalCredentialErrorResponse(protocol: String, error: String): MobileWalletDigitalCredentialResponse =
        DcApiWallet.buildErrorResponse(
            protocol = DcApiRequestProtocol.fromValue(protocol),
            error = WalletPresentFunctionality2.OID4VPErrorCode.entries.firstOrNull { it.code == error }
                ?: throw IllegalArgumentException("Unsupported OpenID4VP error code"),
        ).toMobileDigitalCredentialResponse()

    /** Parses a raw Annex C `DeviceRequest` into the shape a consent screen can render. */
    public fun parseAnnexCDeviceRequest(deviceRequestBase64Url: String): MobileWalletAnnexCParsedRequest =
        annexCEngine.parseDeviceRequest(deviceRequestBase64Url)

    /**
     * Reads an Annex C request out of a platform-neutral Digital Credentials request.
     *
     * Platforms that hand the wallet the whole request up front - Android Credential Manager - deliver
     * the raw `deviceRequest`/`encryptionInfo` pair inside [MobileWalletDigitalCredentialRequest.dataJson].
     * That envelope shape is Annex C's, so it is decoded here rather than in each platform adapter.
     * Apple's provider extension has no such envelope and constructs
     * [MobileWalletAnnexCRequest] from its parsed request instead.
     */
    public fun annexCRequest(request: MobileWalletDigitalCredentialRequest): MobileWalletAnnexCRequest =
        annexCEngine.annexCRequest(request)

    /** Previews an ISO 18013-7 Annex C request without signing or releasing credentials. */
    public suspend fun previewAnnexCPresentation(request: MobileWalletAnnexCRequest): MobileWalletAnnexCPreview = lifecycle.use {
        annexCEngine.preview(request)
    }

    /** Cached status review for a platform-owned Annex C request; no issuer status requests. */
    public suspend fun submitStatusReviewedAnnexCPresentation(
        submission: MobileWalletAnnexCSubmission, reviewedStatus: Map<String, String>,
    ): MobileWalletDigitalCredentialResponse {
        val captured = submission.copy(selectedCredentialOptions = submission.selectedCredentialOptions.toList())
        val revisions = reviewedStatus.toMap()
        val guard = lifecycle.use { wallet.credentialStatuses().reviewGuard(captured.selectedCredentialOptions.mapTo(mutableSetOf()) { it.credentialId }, revisions) }
        guard.check()
        return withContext(guard) { submitAnnexCPresentation(captured) }
    }

    /** Verifies the raw post-consent request, builds device authentication in KMP, and HPKE-encrypts the response. */
    public suspend fun submitAnnexCPresentation(
        submission: MobileWalletAnnexCSubmission,
    ): MobileWalletDigitalCredentialResponse = lifecycle.use {
        annexCEngine.submit(submission)
    }

    /**
     * Resolves and previews an OpenID4VP presentation request without submitting credentials.
     * Protocol failures with a validated response destination are returned as [MobileWalletPresentationPreviewResult.Invalid].
     * Validation errors that cannot be answered safely throw
     * [id.waltid.openid4vp.wallet.UnsafePresentationErrorResponseException], preserving the protocol
     * error and the separate response-safety failure. Resolution failures remain local exceptions.
     */
    public suspend fun previewPresentation(requestUrl: String): MobileWalletPresentationPreviewResult = lifecycle.use {
        val result = WalletPresentationHandler.previewPresentationWithTrust(
            wallet = wallet,
            request = PreviewPresentationRequest(
                requestUrl = Url(requestUrl.trim()),
            ),
            transactionDataTypeRegistry = transactionDataProfiles.toTransactionDataTypeRegistry(),
            clientIdTrustConfiguration = clientIdTrustConfiguration,
            onEvent = ::emitSessionEvent,
        )

        result.toMobilePreview(preferredLocales, transactionDataProfiles)
    }

    /** Captures the disclosures, selected status advisories and payment the host must show before submission. */
    public suspend fun reviewPresentation(
        previewHandle: MobileWalletPresentationPreviewHandle,
        selection: MobileWalletPresentationSelection,
        did: String? = null,
    ): MobileWalletPresentationReview = lifecycle.use {
        MobileWalletPresentationReview(WalletPresentationHandler.retainPresentationReview(wallet,
            SubmitPresentationRequest(PresentationPreviewHandle(previewHandle.value), selection.coreCredentials(),
                selection.coreDisclosures(), did = did), paymentConsentPolicy))
    }

    /** Releases the exact reviewed selection after the host obtains confirmation. */
    public suspend fun submitPresentation(review: MobileWalletPresentationReview): MobileWalletPresentationResult = lifecycle.use {
        WalletPresentationHandler.submitReviewedPresentation(wallet, review.retained,
            transactionDataProfiles.toTransactionDataTypeRegistry(), scaAuthorizer, ::emitSessionEvent).toMobilePresentationResult()
    }

    /** Same selection review for a retained Digital Credentials API request. */
    public suspend fun reviewDigitalCredentialPresentation(
        requestId: String,
        selection: MobileWalletPresentationSelection,
        did: String? = null,
    ): MobileWalletPreparedPresentationReview = lifecycle.use {
        WalletPresentationHandler.reviewDcApiPresentation(wallet,
            SubmitDcApiPresentationRequest(requestId,
                selection.coreCredentials(),
                selection.coreDisclosures(), did = did)
        ).toMobileReview()
    }

    /** Discards a reviewed presentation after local dismissal. */
    public suspend fun discardPresentationPreview(previewHandle: MobileWalletPresentationPreviewHandle): Unit = lifecycle.use {
        WalletPresentationHandler.discardInstancePreview(
            wallet = wallet,
            handle = PresentationPreviewHandle(previewHandle.value),
        )
    }

    /** Rejects a reviewed presentation request and consumes its preview handle. */
    public suspend fun rejectPresentation(
        previewHandle: MobileWalletPresentationPreviewHandle,
        errorCode: MobileWalletPresentationErrorCode? = null,
        errorDescription: String? = null,
    ): MobileWalletPresentationResult = lifecycle.use {
        WalletPresentationHandler.rejectInstancePresentation(
            wallet = wallet,
            request = RejectPresentationRequest(
                previewHandle = PresentationPreviewHandle(previewHandle.value),
                errorCode = errorCode?.errorCode,
                errorDescription = errorDescription,
            ),
            onEvent = ::emitSessionEvent,
        ).toMobilePresentationResult()
    }

    /**
     * Deletes local wallet material owned by this mobile wallet instance.
     *
     * A running wallet or signing-identity operation rejects deletion before changing anything.
     * Close other in-process wallets that share this storage before deletion. Hosts sharing storage with
     * another process must coordinate erasure across processes. Once admitted, cleanup survives cancellation.
     * Platform key/database removal cannot roll back. On failure, retry this method on the same
     * instance; other operations remain closed. Successful deletion is idempotent.
     */
    public suspend fun deleteWallet(): Unit = lifecycle.delete(admit = { persistenceOwner?.storageLease?.beginErase() }) {
        if (!localStoresCleared) {
            val owner = persistenceOwner
            if (persistenceClosed && owner != null) {
                val reopened = owner.openForDeletion()
                try {
                    reopened.clearLocalStores()
                } finally {
                    reopened.close()
                }
            } else {
                clearLocalStores()
            }
            localStoresCleared = true
        }
        closePersistence()
        deleteLocalPersistence()
        persistenceOwner?.storageLease?.finishErase()
    }

    /**
     * Starts the authorization-code browser request for an accepted issuance session.
     *
     * State, PKCE, PAR, client attestation, and the callback continuation are created only by
     * this explicit acceptance transition.
     */
    internal suspend fun beginAuthorizationIssuance(
        sessionId: String,
        registration: IssuanceClientRegistration,
        credentials: List<MobileWalletCredentialSelection>? = null,
    ): WalletIssuanceAuthorization = lifecycle.use {
        issuanceKeys.withSelections(credentials) { prepare, accepted ->
            issuanceSessions.beginAuthorization(sessionId, ClientConfiguration(registration.clientId, listOf(registration.redirectUri)), prepare, accepted)
        }
    }

    /** Continues a pre-authorized session after review and optional transaction-code collection. */
    internal suspend fun continuePreAuthorizedIssuance(
        sessionId: String,
        transactionCode: String?,
        credentials: List<MobileWalletCredentialSelection>?,
        clientId: String?,
    ): WalletIssuanceOutcome = lifecycle.use {
        issuanceKeys.withSelections(credentials) { prepare, accepted ->
            issuanceSessions.continuePreAuthorized(
                sessionId = sessionId,
                transactionCode = transactionCode?.ifBlank { null },
                clientId = clientId,
                prepareCredentials = prepare,
                onCredentialsAccepted = accepted,
            )
        }.alsoRefreshDigitalCredentialRegistration()
    }

    private fun closePersistence() {
        if (persistenceClosed) return
        persistenceOwner?.close?.invoke()
        issuanceProviders.close()
        persistenceClosed = true
    }

    private suspend fun clearLocalStores() {
        wallet.closeCredentialStatusService()
        issuanceSessions.closeSessions()
        proximityCoordinator.shutdown()
        WalletPresentationHandler.clearPreviews(wallet)
        keyStore.listKeys().toList().forEach { key -> keyStore.removeKey(key.keyId) }
        credentialStore.listCredentials().toList().forEach { credential -> credentialStore.removeCredential(credential.id) }
        didStore.listDids().toList().forEach { did -> didStore.removeDid(did.did) }
        // Registry failures cannot prevent removal of the already cleared local database.
        registration.afterMutation()
    }

    private var persistenceClosed = false
    private val lifecycle = MobileWalletLifecycle()

    private var localStoresCleared = false

    private val paymentConsentPolicy = PaymentConsentPolicy(
        PaymentConsentResolver(paymentCredentialIssuers, paymentMetadataHttpClient), preferredLocales,
    )

    private val eventStream = MobileWalletEventStream()

    private val issuanceProviders = MobileWalletIssuanceProviders(walletProviderConfig, attestationConfig, attestationProvider, issuanceHttpClient)

    private val issuanceSessionState = WalletIssuanceSessionState(walletId, issuanceSessionStore)

    private val wallet = Wallet(
        id = walletId,
        keyStores = listOf(keyStore),
        didStore = didStore,
        credentialStores = listOf(credentialStore),
    ).attachReceiptTrust(receiptTrust).attachIssuanceSessionState(issuanceSessionState).also { created ->
        if (issuanceProviders.walletProviderClient != null && issuanceProviders.walletProviderComposition != WalletProviderConfig.Composition.WalletInstance) {
            created.attachKeyAttestationProvider(issuanceProviders.walletProviderClient)
        }
    }

    private val registration = MobileWalletRegistration(wallet, credentialRegistry, preferredLocales, registrationProjection, onDigitalCredentialRegistryChanged)

    private val annexCEngine = MobileWalletAnnexCEngine(
        wallet = wallet,
        readerTrustEvaluator = readerTrustEvaluator,
        registryRecords = { wallet.registryRecords(preferredLocales) },
    )

    private val proximityCoordinator = ProximityCoordinator(
        wallet = wallet,
        bleTransportFactory = proximityTransportFactory,
        nfcHostPlatformAdapter = proximityNfcHostPlatformAdapter,
        wifiAwareTransportFactory = proximityWifiAwareTransportFactory,
    )

    private val issuanceSessions = WalletIssuanceSessionService(
        wallet = wallet,
        attestationAssembler = issuanceProviders.attestationAssembler,
        metadataTrustResolver = credentialIssuerMetadataTrustResolver,
        onEvent = ::emitSessionEvent,
        runtimeState = issuanceSessionState,
        httpClient = issuanceHttpClient,
    )

    private val statusUpdates: Flow<WalletCredentialStatusSnapshot> = flow {
        val service = lifecycle.use { wallet.credentialStatuses() }
        emitAll(service.observe())
    }

    private val signingIdentityManager = createSigningIdentityManager?.invoke(lifecycle, registration::afterMutation)
    private val issuanceKeys = MobileWalletIssuanceKeys(
        keyStore, didStore, signingIdentityManager, defaultKeyUseAuthorizationPolicy, generateAndPersistHolderKey,
    )


    /**
     * Re-registers platform credential metadata whenever a transition actually stored a credential.
     *
     * [WalletIssuanceOutcome.Deferred] and [WalletIssuanceOutcome.Failed] are both included because
     * either can report credentials that were already stored before the session stopped advancing.
     *
     * The outcome is returned unchanged: a credential that was stored has been issued, so a platform
     * registry that could not be updated afterwards must not present itself as an issuance failure.
     */
    private suspend fun WalletIssuanceOutcome.alsoRefreshDigitalCredentialRegistration(): WalletIssuanceOutcome =
        also {
            val storedCredentialIds = when (it) {
                is WalletIssuanceOutcome.Stored -> it.credentialIds
                is WalletIssuanceOutcome.Deferred -> it.storedCredentialIds
                is WalletIssuanceOutcome.Failed -> it.storedCredentialIds
                is WalletIssuanceOutcome.Cancelled -> emptyList()
            }
            if (storedCredentialIds.isNotEmpty()) registration.afterMutation()
        }

    private suspend fun emitSessionEvent(event: WalletSessionEvent) {
        val mobileEvent = event.toMobileWalletEvent()
        eventStream.tryEmit(mobileEvent)
        onEvent(mobileEvent)
    }

}
