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
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.MdocProtocolFeature
import id.walt.mdoc.proximity.MdocProximityProfile
import id.walt.mdoc.proximity.MdocSessionCapabilities
import id.walt.mdoc.proximity.ProximityTransportProvider
import id.walt.mdoc.proximity.ProximityError
import id.walt.mdoc.proximity.QrMdocEngagementSource
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.proximity.mobile.BleBearerPolicy
import id.walt.mdoc.proximity.mobile.BleMdocRoleSelection
import id.walt.mdoc.proximity.mobile.BleMdocRoles
import id.walt.mdoc.proximity.mobile.BleProximityAvailability
import id.walt.mdoc.proximity.mobile.BleProximityTransportConfiguration
import id.walt.mdoc.proximity.mobile.BleProximityTransportFactory
import id.walt.mdoc.proximity.mobile.BleServiceUuid
import id.walt.mdoc.proximity.mobile.NfcHostAvailability
import id.walt.mdoc.proximity.mobile.NfcHostPlatformAdapter
import id.walt.mdoc.proximity.mobile.NfcMdocEngagementConfiguration
import id.walt.mdoc.proximity.mobile.NfcMdocEngagementProfile
import id.walt.mdoc.proximity.mobile.NfcMdocEngagementScope
import id.walt.mdoc.proximity.mobile.NfcMdocEngagementSource
import id.walt.mdoc.proximity.mobile.NfcV2MaximumCommandDataLength
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

internal class ProximityCoordinator(
    private val wallet: Wallet,
    private val bleTransportFactory: BleProximityTransportFactory?,
    private val nfcHostPlatformAdapter: NfcHostPlatformAdapter? = null,
    private val sessionDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val activeMutex = Mutex()
    private var active: ProximitySessionImpl? = null

    suspend fun capabilities(
        configuration: ProximityConfiguration,
    ): ProximityCapabilities {
        val owned = configuration.snapshot()
        val bleConfiguration = owned.retrieval.bleConfiguration
        val bleSelected = bleConfiguration != null
        val bleAvailability = if (bleSelected) {
            bleTransportFactory?.capability(bleConfiguration.roles.toTransportSelection())
                ?: BleProximityAvailability.Unavailable(
                    code = "ble_transport_unavailable",
                    message = "BLE proximity presentation is unavailable on this wallet platform",
                )
        } else null
        val nfcEngagementSelected = owned.engagement.includesNfc
        val nfcRetrievalSelected = owned.retrieval.nfcConfiguration != null
        val nfcV2RetrievalSelected =
            owned.retrieval is MobileWalletProximityRetrievalConfiguration.ProvisionalNfcV2
        val nfcSelected = nfcEngagementSelected || nfcRetrievalSelected
        val nfcAvailability = if (nfcSelected) {
            nfcHostPlatformAdapter?.capability()
                ?: NfcHostAvailability.Unavailable(
                    code = "nfc_host_unavailable",
                    message = "NFC host-card presentation is unavailable on this wallet platform",
                )
        } else null
        return MobileWalletProximityCapabilities(
            profile = owned.profile,
            qrEngagement = availableCapability(owned.engagement.includesQr),
            nfcEngagement = nfcCapability(
                nfcEngagementSelected,
                nfcHostPlatformAdapter != null,
                nfcAvailability,
            ),
            bluetoothLowEnergy = bleCapability(
                bleSelected,
                bleTransportFactory != null,
                bleAvailability,
            ),
            nfcRetrieval = nfcCapability(
                nfcRetrievalSelected,
                nfcHostPlatformAdapter != null,
                nfcAvailability,
            ),
            nfcV2Retrieval = nfcCapability(
                nfcV2RetrievalSelected,
                nfcHostPlatformAdapter != null,
                nfcAvailability,
            ),
            wifiAwareRetrieval = unavailableCapability(
                selected = false,
                code = "wifi_aware_not_implemented",
                message = "Wi-Fi Aware retrieval is not implemented by this wallet build",
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
                bleTransportFactory = bleTransportFactory,
                nfcHostPlatformAdapter = nfcHostPlatformAdapter,
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
    private val configuration: MobileWalletProximityConfiguration,
    private val bleTransportFactory: BleProximityTransportFactory?,
    private val nfcHostPlatformAdapter: NfcHostPlatformAdapter?,
    private val capabilityCheck: suspend () -> MobileWalletProximityCapabilities,
    initialCapabilities: MobileWalletProximityCapabilities,
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
            owner.publish(MobileWalletProximityState.Preparing(configuration.profile))
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
            val engagementSources = buildEngagementSources(
                prerequisites = prerequisites,
                eDeviceKeyBytes = eDeviceKeyBytes,
                engagementFactory = engagementFactory,
            )
            val engine = MdocHolderProtocolEngine(
                eDeviceKey = eDeviceKey,
                engagementSources = engagementSources,
                requestProcessor = processor,
                consentHandler = owner,
                engagementContext = EngagementContext(
                    profile = profile,
                    maximumMessageBytes = configuration.maximumMessageBytes,
                    engagementMode = if (configuration.engagement.includesQr) {
                        MdocEngagementMode.Qr
                    } else {
                        MdocEngagementMode.Nfc
                    },
                ),
                capabilities = capabilities,
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
                is MdocHolderSessionResult.NoData -> owner.publish(ProximityState.NoData(result.exchange))
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

    private fun buildEngagementSources(
        prerequisites: MobileWalletProximityCapabilities,
        eDeviceKeyBytes: ImmutableBytes,
        engagementFactory: MdocDeviceEngagementFactory,
    ): List<id.walt.mdoc.proximity.MdocEngagementSource> {
        val bleConfiguration = configuration.retrieval.bleConfiguration
        fun newBleProviders(): List<ProximityTransportProvider> = bleConfiguration?.let { selected ->
            val factory = requireNotNull(bleTransportFactory) {
                "BLE retrieval passed prerequisites without a platform transport factory"
            }
            listOf(
                factory.create(
                    BleProximityTransportConfiguration(
                        roles = selected.roles.createTransactionRoles(),
                        bearerPolicy = selected.bearerPolicy.toTransportPolicy(),
                        eDeviceKeyBytes = eDeviceKeyBytes,
                    )
                )
            )
        }.orEmpty()

        val nfcRetrieval = configuration.retrieval.nfcConfiguration?.toTransportMethod()
        val qrMayPrepare = prerequisites.qrEngagement.mayStart && listOf(
            prerequisites.bluetoothLowEnergy,
            prerequisites.nfcRetrieval,
            prerequisites.wifiAwareRetrieval,
        ).any { it.mayStart }
        val nfcMayPrepare = prerequisites.nfcEngagement.mayStart && listOf(
            prerequisites.bluetoothLowEnergy,
            prerequisites.nfcRetrieval,
            prerequisites.nfcV2Retrieval,
            prerequisites.wifiAwareRetrieval,
        ).any { it.mayStart }
        fun nfcSource(
            scope: NfcMdocEngagementScope,
            qrProviders: List<ProximityTransportProvider>,
            nfcProviders: List<ProximityTransportProvider>,
        ): NfcMdocEngagementSource = NfcMdocEngagementSource(
            configuration = NfcMdocEngagementConfiguration(scope, nfcRetrieval),
            platform = requireNotNull(nfcHostPlatformAdapter) {
                "NFC passed prerequisites without a host platform adapter"
            },
            alternateTransportProviders = nfcProviders,
            qrTransportProviders = qrProviders,
            engagementFactory = engagementFactory,
        )

        return when (val engagement = configuration.engagement) {
            MobileWalletProximityEngagementConfiguration.QrOnly -> if (
                prerequisites.nfcRetrieval.mayStart && nfcRetrieval != null
            ) {
                listOf(nfcSource(NfcMdocEngagementScope.QrOnly, newBleProviders(), emptyList()))
            } else {
                listOf(QrMdocEngagementSource(newBleProviders(), engagementFactory = engagementFactory))
            }
            is MobileWalletProximityEngagementConfiguration.NfcOnly -> listOf(
                nfcSource(
                    NfcMdocEngagementScope.NfcOnly(engagement.mode.toTransportProfile()),
                    qrProviders = emptyList(),
                    nfcProviders = newBleProviders(),
                )
            )
            is MobileWalletProximityEngagementConfiguration.QrAndNfc -> when {
                qrMayPrepare && nfcMayPrepare -> listOf(
                    nfcSource(
                        NfcMdocEngagementScope.QrAndNfc(engagement.mode.toTransportProfile()),
                        qrProviders = newBleProviders(),
                        nfcProviders = newBleProviders(),
                    )
                )
                nfcMayPrepare -> listOf(
                    nfcSource(
                        NfcMdocEngagementScope.NfcOnly(engagement.mode.toTransportProfile()),
                        qrProviders = emptyList(),
                        nfcProviders = newBleProviders(),
                    )
                )
                qrMayPrepare && prerequisites.nfcRetrieval.mayStart && nfcRetrieval != null -> listOf(
                    nfcSource(
                        NfcMdocEngagementScope.QrOnly,
                        qrProviders = newBleProviders(),
                        nfcProviders = emptyList(),
                    )
                )
                qrMayPrepare -> listOf(
                    QrMdocEngagementSource(newBleProviders(), engagementFactory = engagementFactory)
                )
                else -> error("Proximity prerequisites passed without a viable engagement and retrieval path")
            }
        }
    }

    private suspend fun publishEngineState(engineState: MdocHolderSessionState) {
        val next = when (engineState) {
            MdocHolderSessionState.Idle -> return
            is MdocHolderSessionState.Preparing -> MobileWalletProximityState.Preparing(configuration.profile)
            is MdocHolderSessionState.EngagementReady -> MobileWalletProximityState.EngagementReady(
                engineState.toWalletEngagements()
            )
            is MdocHolderSessionState.Connecting -> MobileWalletProximityState.Connecting(
                engineState.toWalletEngagements()
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
            is MdocHolderSessionState.NoData -> ProximityState.NoData(engineState.exchange)
            is MdocHolderSessionState.Completed ->
                ProximityState.Completed(engineState.exchanges, declined = false)
            is MdocHolderSessionState.Failed -> ProximityState.Failed(engineState.error.toWalletError())
            MdocHolderSessionState.Cancelled -> ProximityState.Cancelled
        }
        owner.publish(next)
    }

    private fun MdocHolderSessionState.EngagementReady.toWalletEngagements():
        List<MobileWalletProximityEngagement> = walletEngagements(qrPayload, engagementModes)

    private fun MdocHolderSessionState.Connecting.toWalletEngagements():
        List<MobileWalletProximityEngagement> = walletEngagements(qrPayload, engagementModes)

    private fun walletEngagements(
        qrPayload: String?,
        modes: Set<MdocEngagementMode>,
    ): List<MobileWalletProximityEngagement> = buildList {
        if (MdocEngagementMode.Qr in modes) {
            add(MobileWalletProximityEngagement.Qr(requireNotNull(qrPayload) { "QR engagement payload is missing" }))
        }
        if (MdocEngagementMode.Nfc in modes) add(MobileWalletProximityEngagement.Nfc)
    }

    private companion object {
        val READER_AUTHENTICATION_ALGORITHMS: Set<Int> = setOf(
            Cose.Algorithm.ES256,
            Cose.Algorithm.ESP256,
            Cose.Algorithm.ES384,
            Cose.Algorithm.ESP384,
            Cose.Algorithm.ES512,
            Cose.Algorithm.ESP512,
            Cose.Algorithm.EdDSA,
            Cose.Algorithm.Ed25519,
            Cose.Algorithm.Ed448,
        )
    }
}

private val MobileWalletProximityRetrievalConfiguration.bleConfiguration:
    MobileWalletProximityBleConfiguration?
    get() = when (this) {
        is MobileWalletProximityRetrievalConfiguration.Conventional -> bluetoothLowEnergy
        is MobileWalletProximityRetrievalConfiguration.ProvisionalNfcV2 -> bluetoothLowEnergy
    }

private val MobileWalletProximityRetrievalConfiguration.nfcConfiguration:
    MobileWalletProximityNfcRetrievalConfiguration?
    get() = when (this) {
        is MobileWalletProximityRetrievalConfiguration.Conventional -> nfc
        is MobileWalletProximityRetrievalConfiguration.ProvisionalNfcV2 -> qrNfc
    }

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private fun MobileWalletProximityNfcRetrievalConfiguration.toTransportMethod(): DeviceRetrievalMethod.Nfc =
    DeviceRetrievalMethod.Nfc(maximumCommandDataLength.toUInt(), maximumResponseDataLength.toUInt())

private fun MobileWalletProximityNfcEngagementMode.toTransportProfile(): NfcMdocEngagementProfile = when (this) {
    MobileWalletProximityNfcEngagementMode.Static -> NfcMdocEngagementProfile.Static
    MobileWalletProximityNfcEngagementMode.Negotiated -> NfcMdocEngagementProfile.Negotiated
    is MobileWalletProximityNfcEngagementMode.ProvisionalV2 ->
        NfcMdocEngagementProfile.ProvisionalV2(NfcV2MaximumCommandDataLength(maximumCommandDataLength))
}

private fun MobileWalletProximityBleBearerPolicy.toTransportPolicy(): BleBearerPolicy = when (this) {
    MobileWalletProximityBleBearerPolicy.GattOnly -> BleBearerPolicy.GattOnly
    MobileWalletProximityBleBearerPolicy.PreferL2cap -> BleBearerPolicy.PreferL2cap
}

private fun MobileWalletProximityBleRoles.toTransportSelection(): BleMdocRoleSelection = when (this) {
    MobileWalletProximityBleRoles.CentralClient -> BleMdocRoleSelection.CENTRAL_CLIENT
    MobileWalletProximityBleRoles.PeripheralServer -> BleMdocRoleSelection.PERIPHERAL_SERVER
    MobileWalletProximityBleRoles.Dual -> BleMdocRoleSelection.DUAL
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

private fun availableCapability(selected: Boolean): MobileWalletProximityTransportCapability =
    MobileWalletProximityTransportCapability(
        implemented = true,
        profilePermitted = true,
        runtime = MobileWalletProximityRuntimeObservation.Available,
        selected = selected,
    )

private fun bleCapability(
    selected: Boolean,
    implemented: Boolean,
    availability: BleProximityAvailability?,
): MobileWalletProximityTransportCapability = when (availability) {
    BleProximityAvailability.Available -> availableCapability(selected)
    is BleProximityAvailability.Unavailable -> unavailableCapability(
        selected = selected,
        implemented = implemented,
        code = availability.code,
        message = availability.message,
        remediationActions = availability.code.toRemediationActions(),
    )
    null -> MobileWalletProximityTransportCapability(
        implemented = implemented, profilePermitted = true, selected = selected,
        runtime = MobileWalletProximityRuntimeObservation.NotChecked,
    )
}

private fun nfcCapability(
    selected: Boolean,
    implemented: Boolean,
    availability: NfcHostAvailability?,
): MobileWalletProximityTransportCapability = when (availability) {
    NfcHostAvailability.Available -> availableCapability(selected)
    is NfcHostAvailability.Unavailable -> unavailableCapability(
        selected = selected,
        implemented = implemented,
        code = availability.code,
        message = availability.message,
        remediationActions = availability.code.toRemediationActions(),
    )
    null -> MobileWalletProximityTransportCapability(
        implemented = implemented, profilePermitted = true, selected = selected,
        runtime = MobileWalletProximityRuntimeObservation.NotChecked,
    )
}

private fun unavailableCapability(
    selected: Boolean,
    implemented: Boolean = false,
    code: String,
    message: String,
    remediationActions: List<MobileWalletProximityRemediationAction> = emptyList(),
): MobileWalletProximityTransportCapability = MobileWalletProximityTransportCapability(
    implemented = implemented,
    profilePermitted = true,
        selected = selected,
    runtime = if (!implemented) MobileWalletProximityRuntimeObservation.NotChecked else
        MobileWalletProximityRuntimeObservation.Unavailable(
            error = MobileWalletProximityError(
                category = MobileWalletProximityErrorCategory.Capability,
                code = code,
                message = message,
                recovery = if (remediationActions.isNotEmpty()) MobileWalletProximityRecovery.RetryPrerequisites else MobileWalletProximityRecovery.None,
            ),
            remediationActions = remediationActions,
        ),
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
    "ble_transport_unavailable" -> listOf(MobileWalletProximityRemediationAction.UseSupportedDevice)
    "ble_state_unknown" -> listOf(MobileWalletProximityRemediationAction.Retry)
    "nfc_powered_off" -> listOf(MobileWalletProximityRemediationAction.EnableNfc)
    "nfc_hce_unsupported",
    "nfc_adapter_unavailable",
    "nfc_host_unavailable",
    "nfc_card_session_unsupported",
    "nfc_system_ineligible" -> listOf(MobileWalletProximityRemediationAction.UseSupportedDevice)
    "nfc_foreground_routing_required",
    "nfc_card_session_active",
    "nfc_session_expired" -> listOf(MobileWalletProximityRemediationAction.Retry)
    else -> emptyList()
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
