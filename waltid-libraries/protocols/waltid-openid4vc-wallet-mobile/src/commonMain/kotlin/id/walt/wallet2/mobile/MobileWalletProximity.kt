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
import id.walt.mdoc.proximity.MdocDeviceEngagementFactory
import id.walt.mdoc.proximity.MdocEngagementMode
import id.walt.mdoc.proximity.MdocHolderProtocolEngine
import id.walt.mdoc.proximity.MdocHolderSessionResult
import id.walt.mdoc.proximity.MdocHolderSessionState
import id.walt.mdoc.proximity.MdocProtocolFeature
import id.walt.mdoc.proximity.MdocProximityProfile
import id.walt.mdoc.proximity.MdocSessionCapabilities
import id.walt.mdoc.proximity.ProximityError as EngineProximityError
import id.walt.mdoc.proximity.mobile.BleBearerPolicy
import id.walt.mdoc.proximity.mobile.BleMdocRoleSelection
import id.walt.mdoc.proximity.mobile.BleMdocRoles
import id.walt.mdoc.proximity.mobile.BleProximityAvailability
import id.walt.mdoc.proximity.mobile.BleProximityTransportConfiguration
import id.walt.mdoc.proximity.mobile.BleProximityTransportFactory
import id.walt.mdoc.proximity.mobile.BleServiceUuid
import id.walt.wallet2.data.Wallet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

internal class ProximityCoordinator(
    private val wallet: Wallet,
    private val transportFactory: BleProximityTransportFactory?,
    private val sessionDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val activeMutex = Mutex()
    private var active: ProximitySessionImpl? = null

    suspend fun capabilities(
        configuration: ProximityConfiguration,
    ): ProximityCapabilities {
        val owned = configuration.snapshot()
        val availability = transportFactory?.capability(owned.bleRoles.toTransportSelection())
            ?: BleProximityAvailability.Unavailable(
                code = "ble_transport_unavailable",
                message = "BLE proximity presentation is unavailable on this wallet platform",
            )
        return ProximityCapabilities(
            profile = owned.profile,
            qrEngagement = ProximityTransportCapability(
                implemented = true,
                profilePermitted = true,
                runtime = ProximityRuntimeObservation.Available,
                selected = ProximityEngagementMethod.Qr in owned.engagementMethods,
            ),
            nfcEngagement = unavailableCapability(
                selected = ProximityEngagementMethod.Nfc in owned.engagementMethods,
            ),
            bluetoothLowEnergy = ProximityTransportCapability(
                implemented = transportFactory != null,
                profilePermitted = true,
                runtime = when (availability) {
                    BleProximityAvailability.Available -> ProximityRuntimeObservation.Available
                    is BleProximityAvailability.Unavailable -> ProximityRuntimeObservation.Unavailable(
                        error = ProximityError(
                            category = ProximityErrorCategory.Capability,
                            code = availability.code,
                            message = availability.message,
                            recovery = ProximityRecovery.RetryPrerequisites,
                        ),
                        remediationActions = availability.code.toRemediationActions(),
                    )
                },
                selected = ProximityRetrievalMethod.BluetoothLowEnergy in owned.retrievalMethods,
            ),
            nfcRetrieval = unavailableCapability(
                selected = ProximityRetrievalMethod.Nfc in owned.retrievalMethods,
            ),
            wifiAwareRetrieval = unavailableCapability(
                selected = ProximityRetrievalMethod.WifiAware in owned.retrievalMethods,
            ),
        )
    }

    suspend fun start(
        configuration: ProximityConfiguration,
    ): ProximitySession {
        val owned = configuration.snapshot()
        return activeMutex.withLock {
            check(active == null) { "A proximity presentation session is already active for this wallet" }
            val initialCapabilities = capabilities(owned)
            val session = ProximitySessionImpl(
                wallet = wallet,
                configuration = owned,
                transportFactory = transportFactory,
                capabilityCheck = { capabilities(owned) },
                initialCapabilities = initialCapabilities,
                sessionDispatcher = sessionDispatcher,
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
}

private class ProximitySessionImpl(
    private val wallet: Wallet,
    private val configuration: ProximityConfiguration,
    private val transportFactory: BleProximityTransportFactory?,
    private val capabilityCheck: suspend () -> ProximityCapabilities,
    initialCapabilities: ProximityCapabilities,
    sessionDispatcher: CoroutineDispatcher,
    private val onTerminal: suspend (ProximitySessionImpl) -> Unit,
) : ProximitySession {
    private val lifecycleJob = SupervisorJob()
    private val scope = CoroutineScope(lifecycleJob + sessionDispatcher)
    private val prerequisiteRetry = Channel<Unit>(Channel.CONFLATED)
    private val owner = ProximitySessionOwner(
        ProximityState.CheckingPrerequisites(initialCapabilities), prerequisiteRetry,
    )
    private val initialCapabilities = initialCapabilities
    override val state: StateFlow<ProximityState> = owner.state
    private lateinit var sessionJob: Job

    @OptIn(DelicateCoroutinesApi::class)
    fun start() {
        // Admission already belongs to this session. Enter its finally block even if cancelled before dispatch.
        sessionJob = scope.launch(start = CoroutineStart.ATOMIC) { runSession() }
    }

    override suspend fun dispatch(action: ProximityAction): ProximityActionResult {
        val result = owner.dispatch(action)
        if (action is ProximityAction.Cancel && result is ProximityActionResult.Accepted) {
            sessionJob.cancelAndJoin()
        }
        return result
    }

    override suspend fun close() {
        owner.cancel()
        if (::sessionJob.isInitialized) sessionJob.cancelAndJoin()
    }

    private suspend fun runSession() {
        var runtime: CryptoRuntime? = null
        var eDeviceKey: Key? = null
        try {
            currentCoroutineContext().ensureActive()
            var prerequisites = initialCapabilities
            while (!prerequisites.mayStart) {
                owner.publish(ProximityState.CheckingPrerequisites(prerequisites))
                prerequisiteRetry.receive()
                prerequisites = capabilityCheck()
            }
            owner.publish(ProximityState.Preparing(configuration.profile))
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
                        ProximityBleBearerPolicy.GattOnly -> BleBearerPolicy.GattOnly
                        ProximityBleBearerPolicy.PreferL2cap -> BleBearerPolicy.PreferL2cap
                    },
                    eDeviceKeyBytes = eDeviceKeyBytes,
                )
            )
            val profile = configuration.profile.toEngineProfile()
            val capabilities = MdocSessionCapabilities.forSession(
                profile = profile,
                key = eDeviceKey,
                selectedFeatures = when (configuration.profile) {
                    ProximityProfile.Iso1801352021 -> emptySet()
                    ProximityProfile.Iso180135Edition2Dis2026,
                    ProximityProfile.EudiArf3Fcaf202608 -> setOf(
                        MdocProtocolFeature.READER_AUTH_ALL,
                        MdocProtocolFeature.EXTENDED_REQUESTS,
                    )
                },
            )
            val processor = ProximityRequestProcessor(
                wallet = wallet,
                configuration = configuration,
                readerAuthenticationAlgorithms = READER_AUTHENTICATION_ALGORITHMS,
            )
            owner.attach(processor)
            val engine = MdocHolderProtocolEngine(
                eDeviceKey = eDeviceKey,
                transportProviders = listOf(transport),
                requestProcessor = processor,
                consentHandler = owner,
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
                withContext(NonCancellable) { stateCollector.cancelAndJoin() }
            }
            when (result) {
                is MdocHolderSessionResult.Completed -> owner.publish(
                    ProximityState.Completed(result.exchanges, declined = false))
                is MdocHolderSessionResult.Declined -> owner.publish(
                    ProximityState.Completed(result.exchange, declined = true))
                is MdocHolderSessionResult.Failed -> owner.publish(
                    ProximityState.Failed(result.error.toWalletError()))
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { owner.cancel() }
            throw cancelled
        } catch (_: Throwable) {
            owner.publish(ProximityState.Failed(
                ProximityError(
                    category = ProximityErrorCategory.Internal,
                    code = "session_failed",
                    message = "The proximity presentation session failed",
                    recovery = ProximityRecovery.StartNewSession,
                )
            ))
        } finally {
            withContext(NonCancellable) {
                owner.cancel()
                eDeviceKey?.capabilities?.deleter?.let { deleter -> runCatching { deleter.delete() } }
                runtime?.let { runCatching { it.close() } }
                prerequisiteRetry.close()
                onTerminal(this@ProximitySessionImpl)
                lifecycleJob.complete()
            }
        }
    }

    private suspend fun publishEngineState(engineState: MdocHolderSessionState) {
        val next = when (engineState) {
            MdocHolderSessionState.Idle -> return
            is MdocHolderSessionState.Preparing -> ProximityState.Preparing(configuration.profile)
            is MdocHolderSessionState.EngagementReady -> ProximityState.EngagementReady(
                listOf(
                    ProximityEngagement.Qr(
                        requireNotNull(engineState.qrPayload) { "QR engagement payload is missing" }
                    )
                )
            )
            is MdocHolderSessionState.Connecting -> ProximityState.Connecting(
                listOf(
                    ProximityEngagement.Qr(
                        requireNotNull(engineState.qrPayload) { "QR engagement payload is missing" }
                    )
                )
            )
            is MdocHolderSessionState.AwaitingRequest ->
                ProximityState.AwaitingRequest(engineState.exchange)
            is MdocHolderSessionState.ReviewRequired -> return // Published by the consent gate after it is dispatchable.
            is MdocHolderSessionState.SendingResponse ->
                ProximityState.SendingResponse(engineState.exchange)
            is MdocHolderSessionState.AwaitingNextRequest ->
                ProximityState.AwaitingNextRequest(engineState.completedExchanges)
            is MdocHolderSessionState.Terminating ->
                ProximityState.Terminating(engineState.exchange)
            is MdocHolderSessionState.Declined ->
                ProximityState.Completed(engineState.exchange, declined = true)
            is MdocHolderSessionState.Completed ->
                ProximityState.Completed(engineState.exchanges, declined = false)
            is MdocHolderSessionState.Failed -> ProximityState.Failed(engineState.error.toWalletError())
            MdocHolderSessionState.Cancelled -> ProximityState.Cancelled
        }
        owner.publish(next)
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

private fun ProximityBleRoles.toTransportSelection(): BleMdocRoleSelection = when (this) {
    ProximityBleRoles.CentralClient -> BleMdocRoleSelection.CENTRAL_CLIENT
    ProximityBleRoles.PeripheralServer -> BleMdocRoleSelection.PERIPHERAL_SERVER
    ProximityBleRoles.Dual -> BleMdocRoleSelection.DUAL
}

private fun ProximityBleRoles.createTransactionRoles(): BleMdocRoles = when (this) {
    ProximityBleRoles.CentralClient -> BleMdocRoles.CentralClient(transactionUuid())
    ProximityBleRoles.PeripheralServer -> BleMdocRoles.PeripheralServer(transactionUuid())
    ProximityBleRoles.Dual -> {
        val reader = transactionUuid()
        var holder = transactionUuid()
        while (holder == reader) holder = transactionUuid()
        BleMdocRoles.Dual(reader, holder)
    }
}

private fun transactionUuid(): BleServiceUuid = BleServiceUuid.parse(Uuid.random().toString())

private fun unavailableCapability(
    selected: Boolean,
): ProximityTransportCapability = ProximityTransportCapability(
    implemented = false,
    profilePermitted = true,
    selected = selected,
    runtime = ProximityRuntimeObservation.NotChecked,
)

private fun String.toRemediationActions(): List<ProximityRemediationAction> = when (this) {
    "ble_permission_missing",
    "ble_permission_not_determined" -> listOf(ProximityRemediationAction.RequestBluetoothPermission)
    "ble_permission_denied",
    "ble_permission_restricted" -> listOf(ProximityRemediationAction.OpenApplicationSettings)
    "ble_powered_off" -> listOf(ProximityRemediationAction.EnableBluetooth)
    "ble_unsupported",
    "ble_scanner_unavailable",
    "ble_advertiser_unavailable",
    "ble_transport_unavailable" -> listOf(ProximityRemediationAction.UseSupportedDevice)
    else -> listOf(ProximityRemediationAction.Retry)
}

private fun ProximityProfile.toEngineProfile(): MdocProximityProfile = when (this) {
    ProximityProfile.Iso1801352021 -> MdocProximityProfile.ISO_18013_5_2021
    ProximityProfile.Iso180135Edition2Dis2026 -> MdocProximityProfile.ISO_18013_5_ED2_DIS_2026
    ProximityProfile.EudiArf3Fcaf202608 -> MdocProximityProfile.EUDI_ARF_3_FCAF_2026_08
}

internal fun EngineProximityError.toWalletError(): ProximityError = ProximityError(
    category = when (code) {
        "changed_submission", "stale_consent", "stale_submission" ->
            ProximityErrorCategory.StaleSubmission
        "engagement_timeout" -> ProximityErrorCategory.Engagement
        "invalid_reader_authentication" -> ProximityErrorCategory.ReaderAuthentication
        "reader_revoked", "trusted_reader_required" -> ProximityErrorCategory.Trust
        "credential_unavailable", "request_unsatisfied" -> ProximityErrorCategory.Credential
        "holder_key_unavailable" -> ProximityErrorCategory.HolderKey
        "application_profile_rejected",
        "application_profile_unsatisfied",
        "application_profile_failed",
        "application_profile_ambiguous",
        "application_profile_invalid" ->
            ProximityErrorCategory.ApplicationProfile
        else -> when (this) {
            is EngineProximityError.Capability -> ProximityErrorCategory.Capability
            is EngineProximityError.Transport -> ProximityErrorCategory.Transport
            is EngineProximityError.Protocol, is EngineProximityError.Security -> ProximityErrorCategory.Protocol
            is EngineProximityError.Policy -> ProximityErrorCategory.Policy
        }
    },
    code = code,
    message = message,
    recovery = if (this is EngineProximityError.Transport || code in setOf("changed_submission", "stale_consent", "stale_submission")) {
        ProximityRecovery.StartNewSession
    } else {
        ProximityRecovery.None
    },
)

internal fun rejectedAction(code: String, message: String): ProximityActionResult.Rejected =
    ProximityActionResult.Rejected(
        ProximityError(
            ProximityErrorCategory.Policy,
            code,
            message,
            recovery = ProximityRecovery.None,
        )
    )
