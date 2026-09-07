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

internal class MobileWalletProximityCoordinator(
    private val wallet: Wallet,
    private val transportFactory: BleProximityTransportFactory?,
) {
    private val activeMutex = Mutex()
    private var active: MobileWalletProximitySessionImpl? = null

    suspend fun capabilities(
        configuration: MobileWalletProximityConfiguration,
    ): MobileWalletProximityCapabilities {
        val owned = configuration.snapshot()
        val availability = transportFactory?.capability(owned.bleRoles.toTransportSelection())
            ?: BleProximityAvailability.Unavailable(
                code = "ble_transport_unavailable",
                message = "BLE proximity presentation is unavailable on this wallet platform",
            )
        return MobileWalletProximityCapabilities(
            profile = owned.profile,
            qrEngagement = MobileWalletProximityTransportCapability(
                implemented = true,
                profilePermitted = true,
                runtime = MobileWalletProximityRuntimeObservation.Available,
                selected = MobileWalletProximityEngagementMethod.Qr in owned.engagementMethods,
            ),
            nfcEngagement = unavailableCapability(
                selected = MobileWalletProximityEngagementMethod.Nfc in owned.engagementMethods,
            ),
            bluetoothLowEnergy = MobileWalletProximityTransportCapability(
                implemented = transportFactory != null,
                profilePermitted = true,
                runtime = when (availability) {
                    BleProximityAvailability.Available -> MobileWalletProximityRuntimeObservation.Available
                    is BleProximityAvailability.Unavailable -> MobileWalletProximityRuntimeObservation.Unavailable(
                        error = MobileWalletProximityError(
                            category = MobileWalletProximityErrorCategory.Capability,
                            code = availability.code,
                            message = availability.message,
                            recovery = MobileWalletProximityRecovery.RetryPrerequisites,
                        ),
                        remediationActions = availability.code.toRemediationActions(),
                    )
                },
                selected = MobileWalletProximityRetrievalMethod.BluetoothLowEnergy in owned.retrievalMethods,
            ),
            nfcRetrieval = unavailableCapability(
                selected = MobileWalletProximityRetrievalMethod.Nfc in owned.retrievalMethods,
            ),
            wifiAwareRetrieval = unavailableCapability(
                selected = MobileWalletProximityRetrievalMethod.WifiAware in owned.retrievalMethods,
            ),
        )
    }

    suspend fun start(
        configuration: MobileWalletProximityConfiguration,
    ): MobileWalletProximitySession {
        val owned = configuration.snapshot()
        return activeMutex.withLock {
            check(active == null) { "A proximity presentation session is already active for this wallet" }
            val initialCapabilities = capabilities(owned)
            val session = MobileWalletProximitySessionImpl(
                wallet = wallet,
                configuration = owned,
                transportFactory = transportFactory,
                capabilityCheck = { capabilities(owned) },
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
}

private class MobileWalletProximitySessionImpl(
    private val wallet: Wallet,
    private val configuration: MobileWalletProximityConfiguration,
    private val transportFactory: BleProximityTransportFactory?,
    private val capabilityCheck: suspend () -> MobileWalletProximityCapabilities,
    initialCapabilities: MobileWalletProximityCapabilities,
    private val onTerminal: suspend (MobileWalletProximitySessionImpl) -> Unit,
) : MobileWalletProximitySession {
    private val lifecycleJob = SupervisorJob()
    private val scope = CoroutineScope(lifecycleJob + Dispatchers.Default)
    private val prerequisiteRetry = Channel<Unit>(Channel.CONFLATED)
    private val owner = MobileWalletProximitySessionOwner(
        MobileWalletProximityState.CheckingPrerequisites(initialCapabilities), prerequisiteRetry,
    )
    private val initialCapabilities = initialCapabilities
    override val state: StateFlow<MobileWalletProximityState> = owner.state
    private lateinit var sessionJob: Job

    fun start() {
        sessionJob = scope.launch { runSession() }
    }

    override suspend fun dispatch(action: MobileWalletProximityAction): MobileWalletProximityActionResult {
        val result = owner.dispatch(action)
        if (action is MobileWalletProximityAction.Cancel && result is MobileWalletProximityActionResult.Accepted) {
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
            var prerequisites = initialCapabilities
            while (!prerequisites.mayStart) {
                owner.publish(MobileWalletProximityState.CheckingPrerequisites(prerequisites))
                prerequisiteRetry.receive()
                prerequisites = capabilityCheck()
            }
            owner.publish(MobileWalletProximityState.Preparing(configuration.profile))
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
                    MobileWalletProximityState.Completed(result.exchanges, declined = false))
                is MdocHolderSessionResult.Declined -> owner.publish(
                    MobileWalletProximityState.Completed(result.exchange, declined = true))
                is MdocHolderSessionResult.Failed -> owner.publish(
                    MobileWalletProximityState.Failed(result.error.toWalletError()))
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { owner.cancel() }
            throw cancelled
        } catch (_: Throwable) {
            owner.publish(MobileWalletProximityState.Failed(
                MobileWalletProximityError(
                    category = MobileWalletProximityErrorCategory.Internal,
                    code = "session_failed",
                    message = "The proximity presentation session failed",
                    recovery = MobileWalletProximityRecovery.StartNewSession,
                )
            ))
        } finally {
            withContext(NonCancellable) {
                owner.cancel()
                eDeviceKey?.capabilities?.deleter?.let { deleter -> runCatching { deleter.delete() } }
                runtime?.let { runCatching { it.close() } }
                prerequisiteRetry.close()
                onTerminal(this@MobileWalletProximitySessionImpl)
                lifecycleJob.complete()
            }
        }
    }

    private suspend fun publishEngineState(engineState: MdocHolderSessionState) {
        val next = when (engineState) {
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
): MobileWalletProximityTransportCapability = MobileWalletProximityTransportCapability(
    implemented = false,
    profilePermitted = true,
    selected = selected,
    runtime = MobileWalletProximityRuntimeObservation.NotChecked,
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
    recovery = if (this is ProximityError.Transport || code in setOf("changed_submission", "stale_consent", "stale_submission")) {
        MobileWalletProximityRecovery.StartNewSession
    } else {
        MobileWalletProximityRecovery.None
    },
)

internal fun rejectedAction(code: String, message: String): MobileWalletProximityActionResult.Rejected =
    MobileWalletProximityActionResult.Rejected(
        MobileWalletProximityError(
            MobileWalletProximityErrorCategory.Policy,
            code,
            message,
            recovery = MobileWalletProximityRecovery.None,
        )
    )
