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
import id.walt.mdoc.proximity.MdocEngagementSource
import id.walt.mdoc.proximity.MdocHolderProtocolEngine
import id.walt.mdoc.proximity.MdocHolderSessionResult
import id.walt.mdoc.proximity.MdocHolderSessionState
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.MdocProtocolFeature
import id.walt.mdoc.proximity.MdocProximityProfile
import id.walt.mdoc.proximity.MdocSessionCapabilities
import id.walt.mdoc.proximity.ProximityTransportKind
import id.walt.mdoc.proximity.ProximityTransportProvider
import id.walt.mdoc.proximity.ProximityException
import id.walt.mdoc.proximity.ProximityError as EngineProximityError
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
import id.walt.mdoc.proximity.mobile.WifiAwareProximityAvailability
import id.walt.mdoc.proximity.mobile.WifiAwareProximityTransportConfiguration
import id.walt.mdoc.proximity.mobile.WifiAwareProximityTransportFactory
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.uuid.Uuid

internal class ProximityCoordinator(
    private val wallet: Wallet,
    private val bleTransportFactory: BleProximityTransportFactory?,
    private val nfcHostPlatformAdapter: NfcHostPlatformAdapter? = null,
    private val sessionDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val wifiAwareTransportFactory: WifiAwareProximityTransportFactory? = null,
) {
    private val activeMutex = Mutex()
    private var active: ProximitySessionImpl? = null

    suspend fun capabilities(
        configuration: ProximityConfiguration,
    ): ProximityCapabilities {
        val owned = configuration.snapshot()
        val bleConfiguration = owned.session.bleConfiguration
        val bleSelected = bleConfiguration != null
        val bleAvailability = if (bleSelected) {
            bleTransportFactory?.capability(bleConfiguration.roles.toTransportSelection())
                ?: BleProximityAvailability.Unavailable(
                    code = "ble_transport_unavailable",
                    message = "BLE proximity presentation is unavailable on this wallet platform",
                )
        } else null
        val nfcEngagementSelected = owned.session !is ProximitySessionConfiguration.Qr
        val nfcRetrievalSelected = owned.session.nfcRetrieval?.nfc != null || owned.session.qrRetrieval?.nfc != null
        val nfcV2RetrievalSelected =
            owned.session is ProximitySessionConfiguration.ProvisionalNfcV2
        val nfcSelected = nfcEngagementSelected || nfcRetrievalSelected
        val nfcAvailability = if (nfcSelected) {
            nfcHostPlatformAdapter?.capability()
                ?: NfcHostAvailability.Unavailable(
                    code = "nfc_host_unavailable",
                    message = "NFC host-card presentation is unavailable on this wallet platform",
                )
        } else null
        val wifiAwareSelected = owned.session.wifiAwareSelected
        val wifiAwareAvailability = if (wifiAwareSelected) {
            wifiAwareTransportFactory?.capability()
                ?: WifiAwareProximityAvailability.Unavailable(
                    implemented = false,
                    code = "wifi_aware_platform_unsupported",
                    message = "Wi-Fi Aware proximity presentation is not implemented on this wallet platform",
                )
        } else null
        return MobileWalletProximityCapabilities(
            profile = owned.profile,
            session = owned.session,
            qrEngagement = availableCapability(owned.session.qrRetrieval != null),
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
            wifiAwareRetrieval = wifiAwareCapability(
                selected = wifiAwareSelected,
                implemented = wifiAwareTransportFactory != null,
                availability = wifiAwareAvailability,
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
                wifiAwareTransportFactory = wifiAwareTransportFactory,
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
    private val bleTransportFactory: BleProximityTransportFactory?,
    private val nfcHostPlatformAdapter: NfcHostPlatformAdapter?,
    private val wifiAwareTransportFactory: WifiAwareProximityTransportFactory?,
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
        approval = configuration.approval,
        canReviewWhileConnected = {
            connectedRoute?.transport != ProximityTransport.Nfc || nfcHostPlatformAdapter?.supportsInSessionUserInteraction != false
        },
    )
    private val initialCapabilities = initialCapabilities
    override val state: StateFlow<ProximityState> = owner.state
    override val sharingPlan: ProximitySharingPlan? get() = owner.sharingPlan
    private lateinit var sessionJob: Job
    private val engineReference = MutableStateFlow<MdocHolderProtocolEngine?>(null)
    override val connectedRoute: ProximityConnectedRoute?
        get() = engineReference.value?.connectedRoute?.let { route ->
            ProximityConnectedRoute(
                engagement = when (route.engagement) {
                    MdocEngagementMode.Qr -> ProximityEngagementMethod.Qr
                    MdocEngagementMode.Nfc -> ProximityEngagementMethod.Nfc
                },
                transport = when (route.transport) {
                    ProximityTransportKind.BLE -> ProximityTransport.BluetoothLowEnergy
                    ProximityTransportKind.NFC -> ProximityTransport.Nfc
                    ProximityTransportKind.WIFI_AWARE -> ProximityTransport.WifiAware
                },
            )
        }

    @OptIn(DelicateCoroutinesApi::class)
    fun start() {
        // Admission already belongs to this session. Enter its finally block even if cancelled before dispatch.
        sessionJob = scope.launch(start = CoroutineStart.ATOMIC) { runSession() }
    }

    override suspend fun dispatch(action: ProximityAction): ProximityActionResult {
        val result = owner.dispatch(action)
        if (action is ProximityAction.Cancel && result is ProximityActionResult.Accepted) {
            (configuration.approval as? ProximityApproval.Prepared)?.sharing?.revoke()
            sessionJob.cancelAndJoin()
        }
        return result
    }

    override suspend fun close() {
        owner.cancel()
        (configuration.approval as? ProximityApproval.Prepared)?.sharing?.revoke()
        if (::sessionJob.isInitialized) sessionJob.cancelAndJoin()
    }

    private suspend fun runSession() {
        var runtime: CryptoRuntime? = null
        var eDeviceKey: Key? = null
        var qrDeviceKey: Key? = null
        var expiryJob: Job? = null
        var revocationJob: Job? = null
        try {
            currentCoroutineContext().ensureActive()
            val prepared = (configuration.approval as? ProximityApproval.Prepared)?.sharing
            if (prepared != null) {
                prepared.claim(wallet)?.let {
                    owner.publish(ProximityState.Failed(it))
                    return
                }
                expiryJob = scope.launch {
                    delay(prepared.remaining)
                    if (owner.invalidatePrepared(approvalError("prepared_sharing_expired", "Prepared sharing expired. Approve again before connecting."))) {
                        sessionJob.cancel()
                    }
                }
                revocationJob = scope.launch {
                    prepared.revoked.first { it }
                    if (owner.invalidatePrepared(approvalError("prepared_sharing_cancelled", "Prepared sharing was cancelled."))) {
                        sessionJob.cancel()
                    }
                }
            }
            var prerequisites = initialCapabilities
            while (!prerequisites.mayStart) {
                owner.publish(ProximityState.CheckingPrerequisites(prerequisites))
                prerequisiteRetry.receive()
                prerequisites = capabilityCheck()
            }
            owner.publish(ProximityState.Preparing(configuration.profile))
            runtime = CryptoRuntime(defaultSoftwareKeyProviders())
            eDeviceKey = runtime.generateSoftwareKey(
                GenerateSoftwareKeyRequest(
                    id = KeyId("mdoc-session-${Uuid.random()}"),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.KEY_AGREEMENT),
                )
            )
            // Distinct keys yield distinct mandatory-derived NAN service names for concurrent routes.
            if (configuration.session.nfcWifiAware && configuration.session.qrRetrieval?.wifiAware == true &&
                prerequisites.nfcMayStart && prerequisites.qrMayStart && prerequisites.wifiAwareRetrieval.mayStart
            ) {
                qrDeviceKey = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
                    id = KeyId("mdoc-qr-session-${Uuid.random()}"),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.KEY_AGREEMENT),
                ))
            }
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
                qrDeviceKey = qrDeviceKey,
                qrDeviceKeyBytes = qrDeviceKey?.let { engagementFactory.encodeEDeviceKeyBytes(it) } ?: eDeviceKeyBytes,
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
                    engagementMode = if (configuration.session.qrRetrieval != null) {
                        MdocEngagementMode.Qr
                    } else {
                        MdocEngagementMode.Nfc
                    },
                ),
                capabilities = capabilities,
            )
            engineReference.value = engine
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
        } catch (failure: ProximityException) {
            owner.publish(ProximityState.Failed(failure.error.toWalletError()))
        } catch (_: Throwable) {
            owner.publish(ProximityState.Failed(
                ProximityError(
                    category = ProximityErrorCategory.Internal,
                    code = "session_preparation_failed",
                    message = "The proximity presentation session could not be prepared",
                    recovery = ProximityRecovery.StartNewSession,
                )
            ))
        } finally {
            withContext(NonCancellable) {
                expiryJob?.cancelAndJoin()
                revocationJob?.cancelAndJoin()
                owner.cancel()
                qrDeviceKey?.capabilities?.deleter?.let { deleter -> runCatching { deleter.delete() } }
                eDeviceKey?.capabilities?.deleter?.let { deleter -> runCatching { deleter.delete() } }
                runtime?.let { runCatching { it.close() } }
                prerequisiteRetry.close()
                onTerminal(this@ProximitySessionImpl)
                lifecycleJob.complete()
            }
        }
    }

    private fun buildEngagementSources(
        prerequisites: ProximityCapabilities,
        eDeviceKeyBytes: ImmutableBytes,
        qrDeviceKey: Key?,
        qrDeviceKeyBytes: ImmutableBytes,
        engagementFactory: MdocDeviceEngagementFactory,
    ): List<MdocEngagementSource> {
        val selected = configuration.session
        fun newProviders(
            ble: MobileWalletProximityBleConfiguration?, wifiAware: Boolean, keyBytes: ImmutableBytes, sharedBleUuid: Boolean = false,
        ): List<ProximityTransportProvider> = buildList {
            if (ble != null && prerequisites.bluetoothLowEnergy.mayStart) add(
                requireNotNull(bleTransportFactory).create(BleProximityTransportConfiguration(
                    roles = ble.roles.createTransactionRoles(sharedBleUuid),
                    bearerPolicy = ble.bearerPolicy.toTransportPolicy(),
                    eDeviceKeyBytes = keyBytes,
                ))
            )
            if (wifiAware && prerequisites.wifiAwareRetrieval.mayStart) add(
                requireNotNull(wifiAwareTransportFactory).create(WifiAwareProximityTransportConfiguration(keyBytes))
            )
        }
        val qrPlan = selected.qrRetrieval
        val nfcDirect = selected.nfcRetrieval?.nfc?.takeIf { prerequisites.nfcRetrieval.mayStart }?.toTransportMethod()
        val qrDirect = qrPlan?.nfc?.takeIf { prerequisites.nfcRetrieval.mayStart }?.toTransportMethod()
        fun nfcSource(scope: NfcMdocEngagementScope): NfcMdocEngagementSource = NfcMdocEngagementSource(
            configuration = NfcMdocEngagementConfiguration(
                scope = scope,
                conventionalRetrieval = nfcDirect.takeUnless { scope is NfcMdocEngagementScope.QrOnly },
                qrRetrieval = qrDirect.takeUnless { scope is NfcMdocEngagementScope.NfcOnly },
            ),
            platform = requireNotNull(nfcHostPlatformAdapter),
            alternateTransportProviders = if (scope is NfcMdocEngagementScope.QrOnly) emptyList() else newProviders(selected.nfcBle, selected.nfcWifiAware, eDeviceKeyBytes,
                sharedBleUuid = selected is MobileWalletProximitySessionConfiguration.ConventionalNfc &&
                    selected.handover == MobileWalletProximityNfcHandover.Static),
            qrTransportProviders = if (scope is NfcMdocEngagementScope.NfcOnly) emptyList() else newProviders(qrPlan?.bluetoothLowEnergy, qrPlan?.wifiAware == true, qrDeviceKeyBytes),
            engagementFactory = engagementFactory,
            qrDeviceKey = qrDeviceKey.takeIf { scope is NfcMdocEngagementScope.QrAndNfc },
        )
        val source = when {
            prerequisites.nfcMayStart -> {
                val profile = when (selected) {
                    is ProximitySessionConfiguration.Qr -> error("QR configuration cannot start NFC engagement")
                    is ProximitySessionConfiguration.ConventionalNfc -> when (selected.handover) {
                        ProximityNfcHandover.Static -> NfcMdocEngagementProfile.Static
                        ProximityNfcHandover.Negotiated -> NfcMdocEngagementProfile.Negotiated
                    }
                    is ProximitySessionConfiguration.ProvisionalNfcV2 ->
                        NfcMdocEngagementProfile.ProvisionalV2(NfcV2MaximumCommandDataLength(selected.maximumCommandDataLength))
                }
                nfcSource(if (prerequisites.qrMayStart) NfcMdocEngagementScope.QrAndNfc(profile) else NfcMdocEngagementScope.NfcOnly(profile))
            }
            prerequisites.qrMayStart && qrDirect != null -> nfcSource(NfcMdocEngagementScope.QrOnly)
            prerequisites.qrMayStart -> QrMdocEngagementSource(newProviders(qrPlan?.bluetoothLowEnergy, qrPlan?.wifiAware == true, qrDeviceKeyBytes), engagementFactory = engagementFactory)
            else -> error("Proximity prerequisites passed without a viable selected route")
        }
        return listOf(source)
    }

    private suspend fun publishEngineState(engineState: MdocHolderSessionState) {
        val next = when (engineState) {
            MdocHolderSessionState.Idle -> return
            is MdocHolderSessionState.Preparing -> ProximityState.Preparing(configuration.profile)
            is MdocHolderSessionState.EngagementReady -> ProximityState.EngagementReady(
                engineState.toWalletEngagements()
            )
            is MdocHolderSessionState.Connecting -> ProximityState.Connecting(
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
            // A protocol result can precede physical response draining, notably Core NFC GET RESPONSE.
            // Publish terminal results only after engine.run() has closed the winning bearer.
            is MdocHolderSessionState.Declined,
            is MdocHolderSessionState.NoData,
            is MdocHolderSessionState.Completed,
            is MdocHolderSessionState.Failed,
            MdocHolderSessionState.Cancelled -> return
        }
        owner.publish(next)
    }

    private fun MdocHolderSessionState.EngagementReady.toWalletEngagements():
        List<ProximityEngagement> = walletEngagements(qrPayload, engagementModes)

    private fun MdocHolderSessionState.Connecting.toWalletEngagements():
        List<ProximityEngagement> = walletEngagements(qrPayload, engagementModes)

    private fun walletEngagements(
        qrPayload: String?,
        modes: Set<MdocEngagementMode>,
    ): List<ProximityEngagement> = buildList {
        if (MdocEngagementMode.Qr in modes) {
            add(ProximityEngagement.Qr(requireNotNull(qrPayload) { "QR engagement payload is missing" }))
        }
        if (MdocEngagementMode.Nfc in modes) add(ProximityEngagement.Nfc)
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

@OptIn(ExperimentalSerializationApi::class)
private fun ProximityNfcRetrievalConfiguration.toTransportMethod(): DeviceRetrievalMethod.Nfc =
    DeviceRetrievalMethod.Nfc(maximumCommandDataLength.toUInt(), maximumResponseDataLength.toUInt())

private fun ProximityBleBearerPolicy.toTransportPolicy(): BleBearerPolicy = when (this) {
    ProximityBleBearerPolicy.GattOnly -> BleBearerPolicy.GattOnly
    ProximityBleBearerPolicy.PreferL2cap -> BleBearerPolicy.PreferL2cap
}

private fun ProximityBleRoles.toTransportSelection(): BleMdocRoleSelection = when (this) {
    ProximityBleRoles.CentralClient -> BleMdocRoleSelection.CENTRAL_CLIENT
    ProximityBleRoles.PeripheralServer -> BleMdocRoleSelection.PERIPHERAL_SERVER
    ProximityBleRoles.Dual -> BleMdocRoleSelection.DUAL
}



private fun MobileWalletProximityBleRoles.createTransactionRoles(sharedUuid: Boolean): BleMdocRoles = when (this) {
    MobileWalletProximityBleRoles.CentralClient -> BleMdocRoles.CentralClient(transactionUuid())
    MobileWalletProximityBleRoles.PeripheralServer -> BleMdocRoles.PeripheralServer(transactionUuid())
    MobileWalletProximityBleRoles.Dual -> {
        val reader = transactionUuid()
        var holder = if (sharedUuid) reader else transactionUuid()
        while (!sharedUuid && holder == reader) holder = transactionUuid()
        BleMdocRoles.Dual(reader, holder)
    }
}

private fun transactionUuid(): BleServiceUuid = BleServiceUuid.parse(Uuid.random().toString())

private fun availableCapability(selected: Boolean): ProximityTransportCapability =
    ProximityTransportCapability(
        implemented = true,
        profilePermitted = true,
        runtime = ProximityRuntimeObservation.Available,
        selected = selected,
    )

private fun bleCapability(
    selected: Boolean,
    implemented: Boolean,
    availability: BleProximityAvailability?,
): ProximityTransportCapability = when (availability) {
    BleProximityAvailability.Available -> availableCapability(selected)
    is BleProximityAvailability.Unavailable -> unavailableCapability(
        selected = selected,
        implemented = implemented,
        code = availability.code,
        message = availability.message,
        remediationActions = availability.code.toRemediationActions(),
    )
    null -> ProximityTransportCapability(
        implemented = implemented, profilePermitted = true, selected = selected,
        runtime = ProximityRuntimeObservation.NotChecked,
    )
}

private fun nfcCapability(
    selected: Boolean,
    implemented: Boolean,
    availability: NfcHostAvailability?,
): ProximityTransportCapability = when (availability) {
    NfcHostAvailability.Available -> availableCapability(selected)
    is NfcHostAvailability.Unavailable -> unavailableCapability(
        selected = selected,
        implemented = implemented,
        code = availability.code,
        message = availability.message,
        remediationActions = availability.code.toRemediationActions(),
    )
    null -> ProximityTransportCapability(
        implemented = implemented, profilePermitted = true, selected = selected,
        runtime = ProximityRuntimeObservation.NotChecked,
    )
}

private fun wifiAwareCapability(
    selected: Boolean,
    implemented: Boolean,
    availability: WifiAwareProximityAvailability?,
): MobileWalletProximityTransportCapability = when (availability) {
    WifiAwareProximityAvailability.Available -> availableCapability(selected)
    is WifiAwareProximityAvailability.Unavailable -> unavailableCapability(
        selected = selected,
        implemented = implemented && availability.implemented,
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
    remediationActions: List<ProximityRemediationAction> = emptyList(),
): ProximityTransportCapability = ProximityTransportCapability(
    implemented = implemented,
    profilePermitted = true,
        selected = selected,
    runtime = if (!implemented) ProximityRuntimeObservation.NotChecked else
        ProximityRuntimeObservation.Unavailable(
            error = ProximityError(
                category = ProximityErrorCategory.Capability,
                code = code,
                message = message,
                recovery = if (remediationActions.isNotEmpty()) ProximityRecovery.RetryPrerequisites else ProximityRecovery.None,
            ),
            remediationActions = remediationActions,
        ),
)

internal fun String.toRemediationActions(): List<ProximityRemediationAction> = when (this) {
    "ble_permission_missing",
    "ble_permission_not_determined" -> listOf(ProximityRemediationAction.RequestBluetoothPermission)
    "ble_permission_denied",
    "ble_permission_restricted" -> listOf(ProximityRemediationAction.OpenApplicationSettings)
    "ble_powered_off" -> listOf(ProximityRemediationAction.EnableBluetooth)
    "ble_unsupported",
    "ble_scanner_unavailable",
    "ble_advertiser_unavailable",
    "ble_transport_unavailable" -> listOf(ProximityRemediationAction.UseSupportedDevice)
    "ble_state_unknown" -> listOf(ProximityRemediationAction.Retry)
    "nfc_powered_off" -> listOf(ProximityRemediationAction.EnableNfc)
    "nfc_hce_unsupported",
    "nfc_adapter_unavailable",
    "nfc_host_unavailable",
    "nfc_card_session_unsupported" -> listOf(ProximityRemediationAction.UseSupportedDevice)
    "nfc_access_not_accepted",
    "nfc_system_ineligible" -> listOf(ProximityRemediationAction.OpenApplicationSettings)
    "nfc_system_unavailable",
    "nfc_session_already_active",
    "nfc_foreground_routing_required",
    "nfc_card_session_active",
    "nfc_session_expired" -> listOf(MobileWalletProximityRemediationAction.Retry)
    "wifi_aware_nearby_permission_missing" ->
        listOf(MobileWalletProximityRemediationAction.RequestNearbyWifiPermission)
    "wifi_aware_local_network_permission_missing" ->
        listOf(MobileWalletProximityRemediationAction.RequestLocalNetworkPermission)
    "wifi_aware_radio_unavailable" -> listOf(MobileWalletProximityRemediationAction.EnableWifi)
    "wifi_aware_api_unsupported",
    "wifi_aware_feature_missing",
    "wifi_aware_manager_unavailable",
    "wifi_aware_ncs_sk_128_unsupported",
    "wifi_aware_bands_unavailable",
    "wifi_aware_platform_unsupported",
    "wifi_aware_ios_api_unsupported" -> listOf(MobileWalletProximityRemediationAction.UseSupportedDevice)
    "wifi_aware_characteristics_unavailable",
    "wifi_aware_resources_unavailable",
    "wifi_aware_capability_check_failed" -> listOf(MobileWalletProximityRemediationAction.Retry)
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
    recovery = if (this is EngineProximityError.Transport || this is EngineProximityError.Capability || code in setOf("request_processing_failed", "response_processing_failed", "changed_submission", "stale_consent", "stale_submission")) {
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
