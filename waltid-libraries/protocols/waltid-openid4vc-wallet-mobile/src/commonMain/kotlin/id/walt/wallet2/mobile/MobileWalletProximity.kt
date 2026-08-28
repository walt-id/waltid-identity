package id.walt.wallet2.mobile

import id.walt.cose.Cose
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.proximity.EngagementContext
import id.walt.mdoc.proximity.MdocConsentDecision
import id.walt.mdoc.proximity.MdocConsentHandler
import id.walt.mdoc.proximity.MdocConsentPrompt
import id.walt.mdoc.proximity.MdocDeviceEngagementFactory
import id.walt.mdoc.proximity.MdocEngagementMode
import id.walt.mdoc.proximity.MdocHolderProtocolEngine
import id.walt.mdoc.proximity.MdocHolderSessionResult
import id.walt.mdoc.proximity.MdocHolderSessionState
import id.walt.mdoc.proximity.MdocProtocolFeature
import id.walt.mdoc.proximity.MdocProximityProfile
import id.walt.mdoc.proximity.MdocSessionCapabilities
import id.walt.mdoc.proximity.ProximityError
import id.walt.mdoc.proximity.mobile.BleBearerPolicy
import id.walt.mdoc.proximity.mobile.BleMdocRoleSelection
import id.walt.mdoc.proximity.mobile.BleMdocRoles
import id.walt.mdoc.proximity.mobile.BleProximityAvailability
import id.walt.mdoc.proximity.mobile.BleProximityTransportConfiguration
import id.walt.mdoc.proximity.mobile.BleProximityTransportFactory
import id.walt.mdoc.proximity.mobile.BleServiceUuid
import id.walt.wallet2.data.Wallet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

internal class MobileWalletProximityCoordinator(
    private val wallet: Wallet,
    private val transportFactory: BleProximityTransportFactory?,
) {
    private val activeMutex = Mutex()
    private var active: MobileWalletProximitySessionImpl? = null

    suspend fun capabilities(
        configuration: MobileWalletProximityConfiguration,
    ): MobileWalletProximityCapabilities {
        val availability = transportFactory?.capability(configuration.bleRoles.toTransportSelection())
            ?: BleProximityAvailability.Unavailable(
                code = "ble_transport_unavailable",
                message = "BLE proximity presentation is unavailable on this wallet platform",
            )
        return MobileWalletProximityCapabilities(
            profile = configuration.profile,
            qrEngagement = MobileWalletProximityTransportCapability(
                implemented = true,
                profilePermitted = true,
                runtimeAvailable = true,
                selected = MobileWalletProximityEngagementMethod.Qr in configuration.engagementMethods,
            ),
            nfcEngagement = unavailableCapability(
                selected = MobileWalletProximityEngagementMethod.Nfc in configuration.engagementMethods,
                code = "nfc_engagement_not_implemented",
                message = "NFC engagement is not implemented by this wallet build",
            ),
            bluetoothLowEnergy = MobileWalletProximityTransportCapability(
                implemented = transportFactory != null,
                profilePermitted = true,
                runtimeAvailable = availability is BleProximityAvailability.Available,
                selected = MobileWalletProximityRetrievalMethod.BluetoothLowEnergy in
                    configuration.retrievalMethods,
                unavailable = (availability as? BleProximityAvailability.Unavailable)?.let {
                    MobileWalletProximityError(
                        category = MobileWalletProximityErrorCategory.Capability,
                        code = it.code,
                        message = it.message,
                        recoverable = true,
                    )
                },
                remediationActions = (availability as? BleProximityAvailability.Unavailable)
                    ?.code
                    ?.toRemediationActions()
                    .orEmpty(),
            ),
            nfcRetrieval = unavailableCapability(
                selected = MobileWalletProximityRetrievalMethod.Nfc in configuration.retrievalMethods,
                code = "nfc_retrieval_not_implemented",
                message = "NFC retrieval is not implemented by this wallet build",
            ),
            wifiAwareRetrieval = unavailableCapability(
                selected = MobileWalletProximityRetrievalMethod.WifiAware in configuration.retrievalMethods,
                code = "wifi_aware_not_implemented",
                message = "Wi-Fi Aware retrieval is not implemented by this wallet build",
            ),
        )
    }

    suspend fun start(
        configuration: MobileWalletProximityConfiguration,
    ): MobileWalletProximitySession = activeMutex.withLock {
        check(active == null) { "A proximity presentation session is already active for this wallet" }
        val initialCapabilities = capabilities(configuration)
        val session = MobileWalletProximitySessionImpl(
            wallet = wallet,
            configuration = configuration,
            transportFactory = transportFactory,
            capabilityCheck = { capabilities(configuration) },
            initialCapabilities = initialCapabilities,
            onTerminal = { completed ->
                activeMutex.withLock {
                    if (active === completed) active = null
                }
            },
        )
        active = session
        session.start()
        session
    }
}

private class MobileWalletProximitySessionImpl(
    private val wallet: Wallet,
    private val configuration: MobileWalletProximityConfiguration,
    private val transportFactory: BleProximityTransportFactory?,
    private val capabilityCheck: suspend () -> MobileWalletProximityCapabilities,
    initialCapabilities: MobileWalletProximityCapabilities,
    private val onTerminal: suspend (MobileWalletProximitySessionImpl) -> Unit,
) : MobileWalletProximitySession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow<MobileWalletProximityState>(
        MobileWalletProximityState.CheckingPrerequisites(initialCapabilities)
    )
    override val state: StateFlow<MobileWalletProximityState> = mutableState.asStateFlow()
    private val actionMutex = Mutex()
    private lateinit var sessionJob: Job
    private var consentGate: MobileWalletProximityConsentGate? = null
    private val prerequisiteRetry = Channel<Unit>(Channel.CONFLATED)

    fun start() {
        sessionJob = scope.launch { runSession() }
    }

    override suspend fun dispatch(
        action: MobileWalletProximityAction,
    ): MobileWalletProximityActionResult = actionMutex.withLock {
        if (action is MobileWalletProximityAction.Cancel) {
            if (mutableState.value.legalActions.contains(MobileWalletProximityActionType.Cancel)) {
                sessionJob.cancelAndJoin()
                return@withLock MobileWalletProximityActionResult.Accepted
            }
            return@withLock rejectedAction("cancel_not_allowed", "Cancellation is not allowed in the current state")
        }
        if (action is MobileWalletProximityAction.RetryPrerequisites) {
            if (mutableState.value is MobileWalletProximityState.CheckingPrerequisites) {
                prerequisiteRetry.trySend(Unit)
                return@withLock MobileWalletProximityActionResult.Accepted
            }
            return@withLock rejectedAction(
                "prerequisite_retry_not_allowed",
                "Prerequisites can only be retried while they are unavailable",
            )
        }
        if (action is MobileWalletProximityAction.ReportRemediation) {
            val current = mutableState.value as? MobileWalletProximityState.CheckingPrerequisites
                ?: return@withLock rejectedAction(
                    "remediation_result_not_allowed",
                    "A remediation result is not expected in the current state",
                )
            if (action.action !in current.capabilities.remediationActions) {
                return@withLock rejectedAction(
                    "unexpected_remediation_result",
                    "The reported remediation was not requested by the current prerequisites",
                )
            }
            if (action.result == MobileWalletProximityHostActionResult.Completed) {
                prerequisiteRetry.trySend(Unit)
            }
            return@withLock MobileWalletProximityActionResult.Accepted
        }
        consentGate?.dispatch(action)
            ?: rejectedAction("action_not_allowed", "The action does not belong to the current review")
    }

    override suspend fun close() {
        if (::sessionJob.isInitialized) sessionJob.cancelAndJoin()
    }

    private suspend fun runSession() {
        var runtime: CryptoRuntime? = null
        var eDeviceKey: Key? = null
        try {
            var prerequisites = (mutableState.value as MobileWalletProximityState.CheckingPrerequisites).capabilities
            while (!prerequisites.mayStart) {
                mutableState.value = MobileWalletProximityState.CheckingPrerequisites(prerequisites)
                prerequisiteRetry.receive()
                prerequisites = capabilityCheck()
            }
            val factory = requireNotNull(transportFactory)
            runtime = CryptoRuntime(defaultSoftwareKeyProviders())
            eDeviceKey = runtime.generateSoftwareKey(
                GenerateSoftwareKeyRequest(
                    id = KeyId("mdoc-session-${Uuid.random()}"),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.KEY_AGREEMENT),
                )
            )
            val engagementFactory = MdocDeviceEngagementFactory()
            val eDeviceKeyBytes = engagementFactory.encodeEDeviceKeyBytes(eDeviceKey)
            val transport = factory.create(
                BleProximityTransportConfiguration(
                    roles = configuration.bleRoles.createTransactionRoles(),
                    bearerPolicy = when (configuration.bearerPolicy) {
                        MobileWalletProximityBleBearerPolicy.GattOnly -> BleBearerPolicy.GattOnly
                        MobileWalletProximityBleBearerPolicy.PreferL2cap -> BleBearerPolicy.PreferL2cap
                    },
                    eDeviceKeyBytes = eDeviceKeyBytes,
                )
            )
            val profile = configuration.profile.toEngineProfile()
            val capabilities = MdocSessionCapabilities.forSession(
                profile = profile,
                key = eDeviceKey,
                selectedFeatures = when (configuration.profile) {
                    MobileWalletProximityProfile.Iso1801352021 -> emptySet()
                    MobileWalletProximityProfile.Iso180135Edition2Dis2026,
                    MobileWalletProximityProfile.EudiArf3Fcaf202608 -> setOf(
                        MdocProtocolFeature.READER_AUTH_ALL,
                        MdocProtocolFeature.EXTENDED_REQUESTS,
                    )
                },
            )
            val processor = MobileWalletProximityRequestProcessor(
                wallet = wallet,
                configuration = configuration,
                readerAuthenticationAlgorithms = READER_AUTHENTICATION_ALGORITHMS,
            )
            val gate = MobileWalletProximityConsentGate(
                processor = processor,
                publishState = { mutableState.value = it },
            )
            consentGate = gate
            val engine = MdocHolderProtocolEngine(
                eDeviceKey = eDeviceKey,
                transportProviders = listOf(transport),
                requestProcessor = processor,
                consentHandler = gate,
                engagementContext = EngagementContext(
                    profile = profile,
                    maximumMessageBytes = configuration.maximumMessageBytes,
                    engagementMode = MdocEngagementMode.Qr,
                ),
                capabilities = capabilities,
                engagementFactory = engagementFactory,
            )
            val stateCollector = scope.launch {
                engine.state.collect(::publishEngineState)
            }
            val result = try {
                engine.run()
            } finally {
                stateCollector.cancelAndJoin()
                gate.cancel()
            }
            when (result) {
                is MdocHolderSessionResult.Completed -> mutableState.value =
                    MobileWalletProximityState.Completed(result.exchanges, declined = false)
                is MdocHolderSessionResult.Declined -> mutableState.value =
                    MobileWalletProximityState.Completed(result.exchange, declined = true)
                is MdocHolderSessionResult.Failed -> mutableState.value =
                    MobileWalletProximityState.Failed(result.error.toWalletError())
            }
        } catch (cancelled: CancellationException) {
            mutableState.value = MobileWalletProximityState.Cancelled
            throw cancelled
        } catch (_: Throwable) {
            mutableState.value = MobileWalletProximityState.Failed(
                MobileWalletProximityError(
                    category = MobileWalletProximityErrorCategory.Internal,
                    code = "session_failed",
                    message = "The proximity presentation session failed",
                    recoverable = true,
                )
            )
        } finally {
            consentGate = null
            eDeviceKey?.capabilities?.deleter?.let { deleter -> runCatching { deleter.delete() } }
            runtime?.let { runCatching { it.close() } }
            onTerminal(this)
        }
    }

    private fun publishEngineState(engineState: MdocHolderSessionState) {
        mutableState.value = when (engineState) {
            MdocHolderSessionState.Idle -> return
            is MdocHolderSessionState.Preparing -> MobileWalletProximityState.Preparing(configuration.profile)
            is MdocHolderSessionState.EngagementReady -> MobileWalletProximityState.EngagementReady(
                listOf(
                    MobileWalletProximityEngagement.Qr(
                        requireNotNull(engineState.qrPayload) { "QR engagement payload is missing" }
                    )
                )
            )
            is MdocHolderSessionState.Connecting -> MobileWalletProximityState.Connecting(
                listOf(
                    MobileWalletProximityEngagement.Qr(
                        requireNotNull(engineState.qrPayload) { "QR engagement payload is missing" }
                    )
                )
            )
            is MdocHolderSessionState.AwaitingRequest ->
                MobileWalletProximityState.AwaitingRequest(engineState.exchange)
            is MdocHolderSessionState.ReviewRequired -> return // Published by the consent gate after it is dispatchable.
            is MdocHolderSessionState.SendingResponse ->
                MobileWalletProximityState.SendingResponse(engineState.exchange)
            is MdocHolderSessionState.AwaitingNextRequest ->
                MobileWalletProximityState.AwaitingNextRequest(engineState.completedExchanges)
            is MdocHolderSessionState.Terminating ->
                MobileWalletProximityState.Terminating(engineState.exchange)
            is MdocHolderSessionState.Declined ->
                MobileWalletProximityState.Completed(engineState.exchange, declined = true)
            is MdocHolderSessionState.Completed ->
                MobileWalletProximityState.Completed(engineState.exchanges, declined = false)
            is MdocHolderSessionState.Failed -> MobileWalletProximityState.Failed(engineState.error.toWalletError())
            MdocHolderSessionState.Cancelled -> MobileWalletProximityState.Cancelled
        }
    }

    private companion object {
        val READER_AUTHENTICATION_ALGORITHMS: Set<Int> = setOf(
            Cose.Algorithm.ES256,
            Cose.Algorithm.ES384,
            Cose.Algorithm.ES512,
            Cose.Algorithm.EdDSA,
        )
    }
}

private class MobileWalletProximityConsentGate(
    private val processor: MobileWalletProximityRequestProcessor,
    private val publishState: (MobileWalletProximityState) -> Unit,
) : MdocConsentHandler {
    private data class Pending(
        val prompt: MdocConsentPrompt,
        val decision: CompletableDeferred<MdocConsentDecision>,
    )

    private val mutex = Mutex()
    private var pending: Pending? = null

    override suspend fun decide(prompt: MdocConsentPrompt): MdocConsentDecision {
        val current = Pending(prompt, CompletableDeferred())
        mutex.withLock {
            check(pending == null) { "A previous proximity consent request is still pending" }
            pending = current
        }
        publishState(MobileWalletProximityState.ReviewRequired(processor.review(prompt)))
        return try {
            current.decision.await()
        } finally {
            mutex.withLock { if (pending === current) pending = null }
        }
    }

    suspend fun dispatch(action: MobileWalletProximityAction): MobileWalletProximityActionResult = mutex.withLock {
        val current = pending
            ?: return@withLock rejectedAction("stale_action", "No proximity review is awaiting this action")
        when (action) {
            is MobileWalletProximityAction.Approve -> {
                processor.accept(current.prompt, action.submission)?.let {
                    return@withLock MobileWalletProximityActionResult.Rejected(it)
                }
                publishState(
                    MobileWalletProximityState.AuthorizingHolderKey(
                        processor.holderAuthorization(current.prompt, action.submission)
                    )
                )
                current.decision.complete(MdocConsentDecision.Approve(current.prompt.bindingToken))
            }
            MobileWalletProximityAction.Decline ->
                current.decision.complete(MdocConsentDecision.Deny(current.prompt.bindingToken))
            MobileWalletProximityAction.Cancel ->
                return@withLock rejectedAction("cancel_routing_error", "Cancellation must be routed through the session")
            MobileWalletProximityAction.RetryPrerequisites,
            is MobileWalletProximityAction.ReportRemediation ->
                return@withLock rejectedAction(
                    "prerequisite_routing_error",
                    "Prerequisite actions must be routed through the session",
                )
        }
        MobileWalletProximityActionResult.Accepted
    }

    suspend fun cancel() {
        mutex.withLock {
            pending?.decision?.cancel()
            pending = null
        }
    }
}

private fun MobileWalletProximityBleRoles.toTransportSelection(): BleMdocRoleSelection = when (this) {
    MobileWalletProximityBleRoles.CentralClient -> BleMdocRoleSelection.CENTRAL_CLIENT
    MobileWalletProximityBleRoles.PeripheralServer -> BleMdocRoleSelection.PERIPHERAL_SERVER
    MobileWalletProximityBleRoles.Dual -> BleMdocRoleSelection.DUAL
}

private fun MobileWalletProximityBleRoles.createTransactionRoles(): BleMdocRoles = when (this) {
    MobileWalletProximityBleRoles.CentralClient -> BleMdocRoles.CentralClient(transactionUuid())
    MobileWalletProximityBleRoles.PeripheralServer -> BleMdocRoles.PeripheralServer(transactionUuid())
    MobileWalletProximityBleRoles.Dual -> {
        val reader = transactionUuid()
        var holder = transactionUuid()
        while (holder == reader) holder = transactionUuid()
        BleMdocRoles.Dual(reader, holder)
    }
}

private fun transactionUuid(): BleServiceUuid = BleServiceUuid.parse(Uuid.random().toString())

private fun unavailableCapability(
    selected: Boolean,
    code: String,
    message: String,
): MobileWalletProximityTransportCapability = MobileWalletProximityTransportCapability(
    implemented = false,
    profilePermitted = true,
    runtimeAvailable = false,
    selected = selected,
    unavailable = MobileWalletProximityError(
        category = MobileWalletProximityErrorCategory.Capability,
        code = code,
        message = message,
        recoverable = false,
    ),
)

private fun String.toRemediationActions(): List<MobileWalletProximityRemediationAction> = when (this) {
    "ble_permission_missing",
    "ble_permission_not_determined" -> listOf(MobileWalletProximityRemediationAction.RequestBluetoothPermission)
    "ble_permission_denied",
    "ble_permission_restricted" -> listOf(MobileWalletProximityRemediationAction.OpenApplicationSettings)
    "ble_powered_off" -> listOf(MobileWalletProximityRemediationAction.EnableBluetooth)
    "ble_unsupported",
    "ble_scanner_unavailable",
    "ble_advertiser_unavailable",
    "ble_transport_unavailable" -> listOf(MobileWalletProximityRemediationAction.UseSupportedDevice)
    else -> listOf(MobileWalletProximityRemediationAction.Retry)
}

private fun MobileWalletProximityProfile.toEngineProfile(): MdocProximityProfile = when (this) {
    MobileWalletProximityProfile.Iso1801352021 -> MdocProximityProfile.ISO_18013_5_2021
    MobileWalletProximityProfile.Iso180135Edition2Dis2026 -> MdocProximityProfile.ISO_18013_5_ED2_DIS_2026
    MobileWalletProximityProfile.EudiArf3Fcaf202608 -> MdocProximityProfile.EUDI_ARF_3_FCAF_2026_08
}

internal fun ProximityError.toWalletError(): MobileWalletProximityError = MobileWalletProximityError(
    category = when (code) {
        "changed_submission", "stale_consent", "stale_submission" ->
            MobileWalletProximityErrorCategory.StaleSubmission
        "engagement_timeout" -> MobileWalletProximityErrorCategory.Engagement
        "invalid_reader_authentication" -> MobileWalletProximityErrorCategory.ReaderAuthentication
        "reader_revoked", "trusted_reader_required" -> MobileWalletProximityErrorCategory.Trust
        "credential_unavailable", "request_unsatisfied" -> MobileWalletProximityErrorCategory.Credential
        "holder_key_unavailable" -> MobileWalletProximityErrorCategory.HolderKey
        "application_profile_rejected",
        "application_profile_unsatisfied",
        "application_profile_failed",
        "application_profile_ambiguous",
        "application_profile_invalid" ->
            MobileWalletProximityErrorCategory.ApplicationProfile
        else -> when (this) {
            is ProximityError.Capability -> MobileWalletProximityErrorCategory.Capability
            is ProximityError.Transport -> MobileWalletProximityErrorCategory.Transport
            is ProximityError.Protocol, is ProximityError.Security -> MobileWalletProximityErrorCategory.Protocol
            is ProximityError.Policy -> MobileWalletProximityErrorCategory.Policy
        }
    },
    code = code,
    message = message,
    recoverable = this is ProximityError.Capability ||
        this is ProximityError.Transport ||
        code in setOf("changed_submission", "stale_consent", "stale_submission"),
)

private fun rejectedAction(code: String, message: String): MobileWalletProximityActionResult.Rejected =
    MobileWalletProximityActionResult.Rejected(
        MobileWalletProximityError(
            MobileWalletProximityErrorCategory.Policy,
            code,
            message,
            recoverable = true,
        )
    )
