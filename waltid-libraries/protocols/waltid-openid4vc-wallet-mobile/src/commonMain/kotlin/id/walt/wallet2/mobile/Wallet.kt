@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

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
import id.walt.wallet2.data.Wallet as CoreWallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.handlers.KeyAttestationProvider
import id.walt.wallet2.handlers.KeyAttestationProviderResolver
import id.walt.wallet2.handlers.PresentationPreviewHandle as CorePresentationPreviewHandle
import id.walt.wallet2.handlers.PreviewDcApiPresentationRequest
import id.walt.wallet2.handlers.PreviewPresentationRequest
import id.walt.wallet2.handlers.RejectPresentationRequest
import id.walt.wallet2.handlers.SubmitDcApiPresentationRequest
import id.walt.wallet2.handlers.SubmitPresentationRequest
import id.walt.wallet2.handlers.WalletIssuanceAuthorization
import id.walt.wallet2.handlers.WalletIssuanceAuthorizationCallback
import id.walt.wallet2.handlers.WalletIssuanceContinuation
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.handlers.WalletIssuanceSessionService
import id.walt.wallet2.handlers.WalletIssuanceSessionState
import id.walt.wallet2.handlers.WalletIssuanceSessionStore
import id.walt.wallet2.handlers.WalletPresentationHandler
import id.walt.wallet2.handlers.WalletScaPresentationAuthorizer
import id.walt.wallet2.issuance.CredentialReceiptTrust
import id.walt.wallet2.mobile.credentials.CredentialWithStatus
import id.walt.wallet2.mobile.credentials.WalletCredential
import id.walt.wallet2.mobile.credentials.toMobileWalletCredential
import id.walt.wallet2.mobile.digitalcredentials.AnnexCParsedRequest
import id.walt.wallet2.mobile.digitalcredentials.AnnexCPreview
import id.walt.wallet2.mobile.digitalcredentials.AnnexCRequest
import id.walt.wallet2.mobile.digitalcredentials.AnnexCSubmission
import id.walt.wallet2.mobile.digitalcredentials.CredentialRegistrationResult
import id.walt.wallet2.mobile.digitalcredentials.CredentialRegistry
import id.walt.wallet2.mobile.digitalcredentials.DigitalCredentialCapabilities
import id.walt.wallet2.mobile.digitalcredentials.DigitalCredentialPreview
import id.walt.wallet2.mobile.digitalcredentials.DigitalCredentialProtocols
import id.walt.wallet2.mobile.digitalcredentials.DigitalCredentialRequest
import id.walt.wallet2.mobile.digitalcredentials.DigitalCredentialRequestInfo
import id.walt.wallet2.mobile.digitalcredentials.DigitalCredentialResponse
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletAnnexCEngine
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletRegistration
import id.walt.wallet2.mobile.digitalcredentials.MobileWalletRegistryProjection
import id.walt.wallet2.mobile.digitalcredentials.StaleRegistryEntryException
import id.walt.wallet2.mobile.digitalcredentials.UnavailableCredentialRegistry
import id.walt.wallet2.mobile.digitalcredentials.registryRecords
import id.walt.wallet2.mobile.digitalcredentials.toMobileDigitalCredentialResponse
import id.walt.wallet2.mobile.digitalcredentials.toDigitalCredentialPreview
import id.walt.wallet2.mobile.identity.SigningIdentityManager
import id.walt.wallet2.mobile.identity.WalletKeyType
import id.walt.wallet2.mobile.internal.encodeJsonObject
import id.walt.wallet2.mobile.issuance.DeferredIssuanceId
import id.walt.wallet2.mobile.issuance.IssuanceAuthorizationId
import id.walt.wallet2.mobile.issuance.IssuanceClientRegistration
import id.walt.wallet2.mobile.issuance.IssuanceCredentialSelection
import id.walt.wallet2.mobile.issuance.IssuanceHolderBinding
import id.walt.wallet2.mobile.issuance.IssuanceHolders
import id.walt.wallet2.mobile.issuance.IssuanceRequest
import id.walt.wallet2.mobile.issuance.IssuanceSession
import id.walt.wallet2.mobile.issuance.IssuanceSessionId
import id.walt.wallet2.mobile.issuance.MobileWalletIssuanceKeys
import id.walt.wallet2.mobile.issuance.MobileWalletIssuanceProviders
import id.walt.wallet2.mobile.issuance.WalletAttestationConfig
import id.walt.wallet2.mobile.issuance.WalletProviderConfig
import id.walt.wallet2.mobile.presentation.PresentationErrorCode
import id.walt.wallet2.mobile.presentation.PresentationPreviewHandle
import id.walt.wallet2.mobile.presentation.PresentationPreviewResult
import id.walt.wallet2.mobile.presentation.PresentationResult
import id.walt.wallet2.mobile.presentation.PresentationReview
import id.walt.wallet2.mobile.presentation.PresentationSelection
import id.walt.wallet2.mobile.presentation.ReviewedPresentationClaims
import id.walt.wallet2.mobile.presentation.TransactionDataProfile
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
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
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
 * Use [WalletFactory] to create instances with the correct platform storage, key provider,
 * and SQLDelight driver. The facade keeps the public mobile API intentionally small while delegating
 * protocol work to the core wallet handlers.
 */
public class Wallet internal constructor(
    walletId: String,
    private val keyStore: MobileWalletKeyStore,
    private val didStore: WalletDidStore,
    private val credentialStore: WalletCredentialStore,
    private val issuanceSessionStore: WalletIssuanceSessionStore? = null,
    private val runKeyUseAuthorizationPreflight: suspend (WalletKeyType, KeyUseAuthorizationPolicy) -> KeyUseAuthorizationSupport =
        { _, _ -> error("This Wallet does not support key-use authorization preflight") },
    private val defaultKeyUseAuthorizationPolicy: KeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
    private val generateAndPersistHolderKey: suspend (WalletKeyType, KeyUseAuthorizationPolicy) -> id.walt.crypto2.keys.Key =
        { _, _ -> error("Holder key creation requires the persistent mobile wallet factory") },
    attestationConfig: WalletAttestationConfig? = null,
    attestationProvider: WalletAttestationProvider? = null,
    walletProviderConfig: WalletProviderConfig? = null,
    private val preferredLocales: List<String> = emptyList(),
    receiptTrust: CredentialReceiptTrust? = null,
    private val transactionDataProfiles: List<TransactionDataProfile> = emptyList(),
    private val clientIdTrustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
    private val credentialIssuerMetadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
    private val credentialRegistry: CredentialRegistry = UnavailableCredentialRegistry,
    registrationProjection: MobileWalletRegistryProjection = MobileWalletRegistryProjection.Full,
    private val readerTrustEvaluator: ReaderTrustEvaluator = MdocReaderAuthenticationTrustEvaluator,
    private val onEvent: suspend (WalletEvent) -> Unit = {},
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
    public val events: Flow<WalletEvent>
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
     * The completed replacement is published before creation-option registration or host notification.
     * Cancelling either later operation preserves that result. The StateFlow retains its last value after
     * close. The host owns and cancels its collection.
     */
    public val digitalCredentialRegistration: StateFlow<CredentialRegistrationResult?>
        get() = registration.state

    /**
     * Attaches the provider used when an issuer requires key attestation in a credential proof.
     * This runtime dependency must be reattached whenever the wallet is recreated.
     */
    public fun attachKeyAttestationProvider(provider: KeyAttestationProvider): Wallet = apply {
        wallet.attachKeyAttestationProvider(provider)
    }

    /** Attaches issuer-specific key attestation support. Supply it again after wallet recreation. */
    public fun attachKeyAttestationProviderResolver(
        resolver: KeyAttestationProviderResolver,
    ): Wallet = apply {
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
     * [IssuanceHolders.NewKeys]; preview never generates keys.
     */
    public suspend fun createIssuanceHolderKeys(
        count: Int,
        keyType: WalletKeyType? = null,
        didMethod: String = "key",
        keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy? = null,
    ): List<IssuanceHolderBinding> = lifecycle.use {
        issuanceKeys.create(count, keyType, didMethod, keyUseAuthorizationPolicy)
    }

    /** Checks whether a key-use authorization request is supported without creating or persisting a key. */
    public suspend fun keyUseAuthorizationPreflight(
        keyType: WalletKeyType = WalletKeyType.secp256r1,
        keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy = defaultKeyUseAuthorizationPolicy,
    ): KeyUseAuthorizationSupport = lifecycle.use {
        runKeyUseAuthorizationPreflight(keyType, keyUseAuthorizationPolicy)
    }

    /**
     * Resolves an offer and starts a bound OpenID4VCI 1.0 issuance session.
     *
     * Accept the returned grant after showing its credential and issuer preview.
     */
    public suspend fun startIssuance(request: IssuanceRequest): IssuanceSession = lifecycle.use {
        IssuanceSession.from(issuanceSessions.startOffer(issuanceKeys.offerRequest(request), preferredLocales), this)
    }

    /** Reopens an offer that is still awaiting acceptance, including after wallet recreation. */
    public suspend fun resumeIssuance(id: IssuanceSessionId): IssuanceSession? = lifecycle.use {
        issuanceSessions.reviewOffer(id.value)?.let { IssuanceSession.from(it, this) }
    }

    /**
     * Validates and consumes a browser callback bound to an authorization-code session.
     *
     * The callback target, OAuth state, authorization code, PKCE verifier, issuer metadata, and
     * selected holder key are all taken from or checked against the authoritative session record.
     */
    public suspend fun completeAuthorization(
        id: IssuanceAuthorizationId,
        callbackUri: String,
    ): WalletIssuanceOutcome = lifecycle.use {
        issuanceSessions.continueAuthorization(
            WalletIssuanceAuthorizationCallback(sessionId = id.value, callbackUri = callbackUri),
        ).alsoRefreshDigitalCredentialRegistration()
    }

    /** Cancels an active issuance session and discards its protocol continuation material. */
    public suspend fun cancelIssuance(sessionId: IssuanceSessionId): WalletIssuanceOutcome = lifecycle.use {
        issuanceSessions.cancel(sessionId.value)
    }

    /** Recovers retained deferred handles after an interrupted flow or wallet recreation. */
    public suspend fun listDeferredIssuance(): List<WalletIssuanceContinuation> = lifecycle.use {
        issuanceSessions.listIssuanceContinuations()
    }

    /** Polls a typed deferred credential result returned by a previous issuance continuation. */
    public suspend fun resumeDeferredIssuance(
        id: DeferredIssuanceId,
    ): WalletIssuanceOutcome = lifecycle.use {
        issuanceSessions.resumeDeferred(id.value).alsoRefreshDigitalCredentialRegistration()
    }

    /**
     * Lists all credentials currently stored in the mobile wallet.
     *
     * @return Credential entries, including display JSON, ordered by the underlying credential store.
     */
    public suspend fun credentials(): List<WalletCredential> = lifecycle.use {
        wallet.streamAllCredentials().map { it.toMobileWalletCredential() }.toList()
    }

    /** Reads credentials with typed, local status metadata without rebuilding them for status updates. */
    public suspend fun credentialReads(): List<CredentialWithStatus> = lifecycle.use {
        wallet.readCredentialsWithStatus().map { read ->
            CredentialWithStatus(read.credential.toMobileWalletCredential(), read.status, read.evaluatedAt)
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
    public fun digitalCredentialCapabilities(): DigitalCredentialCapabilities =
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
    public suspend fun refreshDigitalCredentialRegistration(): CredentialRegistrationResult = lifecycle.use {
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
     * unsigned request, the returned [DigitalCredentialPreview.verifiedOrigin] is the
     * authenticated requester identity and [DigitalCredentialRequestInfo.clientId]
     * remains null because the untrusted request-supplied `client_id` is ignored. For a signed
     * request, [DigitalCredentialRequestInfo.clientId] is the authenticated Request
     * Object client identifier after signature verification against [clientIdTrustConfiguration].
     * Unsigned (`openid4vp-v1-unsigned`) and signed (`openid4vp-v1-signed`) Digital Credentials
     * requests are accepted. Multisigned is not.
     */
    public suspend fun previewDigitalCredentialPresentation(
        request: DigitalCredentialRequest,
    ): DigitalCredentialPreview = lifecycle.use {
        require(request.protocol != DigitalCredentialProtocols.ISO_MDOC_ANNEX_C) {
            "ISO 18013-7 Annex C requests use the dedicated Annex C facade"
        }
        val currentRecords = wallet.registryRecords(preferredLocales)
        val credentialIdsByRegistryId = currentRecords.associate { it.registryEntryId to it.credentialId }
        val selectedCredentialIds = request.selectedRegistryEntryIds.map { registryId ->
            credentialIdsByRegistryId[registryId] ?: throw StaleRegistryEntryException(registryId)
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
        result.toDigitalCredentialPreview(request.protocol, preferredLocales, transactionDataProfiles)
    }

    /** Resolves payment instructions for the chosen DC API credentials without signing. */
    public suspend fun prepareDigitalCredentialPaymentConsent(
        requestId: String,
        selection: PresentationSelection,
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
        selection: PresentationSelection,
        reviewedStatus: Map<String, String>,
        did: String? = null, paymentConsentRevision: String? = null,
    ): DigitalCredentialResponse {
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
        selection: PresentationSelection,
        did: String? = null,
        paymentConsentRevision: String? = null,
    ): DigitalCredentialResponse = lifecycle.use {
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
    public fun digitalCredentialErrorResponse(protocol: String, error: String): DigitalCredentialResponse =
        DcApiWallet.buildErrorResponse(
            protocol = DcApiRequestProtocol.fromValue(protocol),
            error = WalletPresentFunctionality2.OID4VPErrorCode.entries.firstOrNull { it.code == error }
                ?: throw IllegalArgumentException("Unsupported OpenID4VP error code"),
        ).toMobileDigitalCredentialResponse()

    /** Parses a raw Annex C `DeviceRequest` into the shape a consent screen can render. */
    public fun parseAnnexCDeviceRequest(deviceRequestBase64Url: String): AnnexCParsedRequest =
        annexCEngine.parseDeviceRequest(deviceRequestBase64Url)

    /**
     * Reads an Annex C request out of a platform-neutral Digital Credentials request.
     *
     * Platforms that hand the wallet the whole request up front - Android Credential Manager - deliver
     * the raw `deviceRequest`/`encryptionInfo` pair inside [DigitalCredentialRequest.dataJson].
     * That envelope shape is Annex C's, so it is decoded here rather than in each platform adapter.
     * Apple's provider extension has no such envelope and constructs
     * [AnnexCRequest] from its parsed request instead.
     */
    public fun annexCRequest(request: DigitalCredentialRequest): AnnexCRequest =
        annexCEngine.annexCRequest(request)

    /** Previews an ISO 18013-7 Annex C request without signing or releasing credentials. */
    public suspend fun previewAnnexCPresentation(request: AnnexCRequest): AnnexCPreview = lifecycle.use {
        annexCEngine.preview(request)
    }

    /** Cached status review for a platform-owned Annex C request; no issuer status requests. */
    public suspend fun submitStatusReviewedAnnexCPresentation(
        submission: AnnexCSubmission, reviewedStatus: Map<String, String>,
    ): DigitalCredentialResponse {
        val captured = submission.copy(selectedCredentialOptions = submission.selectedCredentialOptions.toList())
        val revisions = reviewedStatus.toMap()
        val guard = lifecycle.use { wallet.credentialStatuses().reviewGuard(captured.selectedCredentialOptions.mapTo(mutableSetOf()) { it.credentialId }, revisions) }
        guard.check()
        return withContext(guard) { submitAnnexCPresentation(captured) }
    }

    /** Verifies the raw post-consent request, builds device authentication in KMP, and HPKE-encrypts the response. */
    public suspend fun submitAnnexCPresentation(
        submission: AnnexCSubmission,
    ): DigitalCredentialResponse = lifecycle.use {
        annexCEngine.submit(submission)
    }

    /**
     * Resolves and previews an OpenID4VP presentation request without submitting credentials.
     * Protocol failures with a validated response destination are returned as [PresentationPreviewResult.Invalid].
     * Validation errors that cannot be answered safely throw
     * [id.waltid.openid4vp.wallet.UnsafePresentationErrorResponseException], preserving the protocol
     * error and the separate response-safety failure. Resolution failures remain local exceptions.
     */
    public suspend fun previewPresentation(requestUrl: String): PresentationPreviewResult = lifecycle.use {
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
        previewHandle: PresentationPreviewHandle,
        selection: PresentationSelection,
        did: String? = null,
    ): PresentationReview = lifecycle.use {
        PresentationReview(WalletPresentationHandler.retainPresentationReview(wallet,
            SubmitPresentationRequest(CorePresentationPreviewHandle(previewHandle.value), selection.coreCredentials(),
                selection.coreDisclosures(), did = did), paymentConsentPolicy))
    }

    /** Releases the exact reviewed selection after the host obtains confirmation. */
    public suspend fun submitPresentation(review: PresentationReview): PresentationResult = lifecycle.use {
        WalletPresentationHandler.submitReviewedPresentation(wallet, review.retained,
            transactionDataProfiles.toTransactionDataTypeRegistry(), scaAuthorizer, ::emitSessionEvent).toMobilePresentationResult()
    }

    /** Same selection review for a retained Digital Credentials API request. */
    public suspend fun reviewDigitalCredentialPresentation(
        requestId: String,
        selection: PresentationSelection,
        did: String? = null,
    ): ReviewedPresentationClaims = lifecycle.use {
        WalletPresentationHandler.reviewDcApiPresentation(wallet,
            SubmitDcApiPresentationRequest(requestId,
                selection.coreCredentials(),
                selection.coreDisclosures(), did = did)
        ).toMobileReview()
    }

    /** Discards a reviewed presentation after local dismissal. */
    public suspend fun discardPresentationPreview(previewHandle: PresentationPreviewHandle): Unit = lifecycle.use {
        WalletPresentationHandler.discardInstancePreview(
            wallet = wallet,
            handle = CorePresentationPreviewHandle(previewHandle.value),
        )
    }

    /** Rejects a reviewed presentation request and consumes its preview handle. */
    public suspend fun rejectPresentation(
        previewHandle: PresentationPreviewHandle,
        errorCode: PresentationErrorCode? = null,
        errorDescription: String? = null,
    ): PresentationResult = lifecycle.use {
        WalletPresentationHandler.rejectInstancePresentation(
            wallet = wallet,
            request = RejectPresentationRequest(
                previewHandle = CorePresentationPreviewHandle(previewHandle.value),
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
    public suspend fun deleteLocalData(): Unit = lifecycle.delete(admit = { persistenceOwner?.storageLease?.beginErase() }) {
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
        credentials: List<IssuanceCredentialSelection>? = null,
    ): WalletIssuanceAuthorization = lifecycle.use {
        issuanceKeys.withSelections(credentials) { prepare, accepted ->
            issuanceSessions.beginAuthorization(sessionId, ClientConfiguration(registration.clientId, listOf(registration.redirectUri)), prepare, accepted)
        }
    }

    /** Continues a pre-authorized session after review and optional transaction-code collection. */
    internal suspend fun continuePreAuthorizedIssuance(
        sessionId: String,
        transactionCode: String?,
        credentials: List<IssuanceCredentialSelection>?,
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
        WalletPresentationHandler.closeInstancePreviews(wallet)
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

    private val wallet = CoreWallet(
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
