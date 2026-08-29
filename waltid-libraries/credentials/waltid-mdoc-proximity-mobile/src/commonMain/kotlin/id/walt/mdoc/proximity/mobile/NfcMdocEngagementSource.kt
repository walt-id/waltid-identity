@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.proximity.EngagementContext
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.MdocDeviceEngagementFactory
import id.walt.mdoc.proximity.MdocDeviceEngagementPlacement
import id.walt.mdoc.proximity.MdocEngagedConnection
import id.walt.mdoc.proximity.MdocEngagementMode
import id.walt.mdoc.proximity.MdocEngagementPreparationContext
import id.walt.mdoc.proximity.MdocEngagementReadiness
import id.walt.mdoc.proximity.MdocEngagementSource
import id.walt.mdoc.proximity.MdocSessionHandover
import id.walt.mdoc.proximity.PreparedMdocEngagement
import id.walt.mdoc.proximity.PreparedTransport
import id.walt.mdoc.proximity.PreparedTransportId
import id.walt.mdoc.proximity.PreparedTransports
import id.walt.mdoc.proximity.ProximityCapability
import id.walt.mdoc.proximity.ProximityCloseReason
import id.walt.mdoc.proximity.ProximityConnection
import id.walt.mdoc.proximity.ProximityError
import id.walt.mdoc.proximity.ProximityException
import id.walt.mdoc.proximity.ProximityTransportKind
import id.walt.mdoc.proximity.ProximityTransportProvider
import id.walt.mdoc.proximity.ReaderSelectedTransportOffer
import id.walt.mdoc.proximity.ReaderSelectedTransportProvider
import id.walt.mdoc.proximity.TransportCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/** Conventional Connection Handover or the explicitly provisional NFCv2 path for one session. */
public sealed interface NfcMdocEngagementProfile {
    /** NFC Forum Static Handover with holder-selected retrieval methods. */
    public data object Static : NfcMdocEngagementProfile

    /** NFC Forum Negotiated Handover with a reader-selected retrieval method. */
    public data object Negotiated : NfcMdocEngagementProfile

    /**
     * Provisional second-edition NFC Engagement v2 behavior pinned to the selected source contract.
     *
     * @property maximumCommandDataLength Maximum command data accepted by the holder NFCv2 application.
     */
    public data class ProvisionalV2(
        public val maximumCommandDataLength: NfcV2MaximumCommandDataLength,
    ) : NfcMdocEngagementProfile
}

/** Selects which engagement paths are prepared for one proximity session. */
public sealed interface NfcMdocEngagementScope {
    /** QR is the only engagement path; conventional NFC remains available as a retrieval bearer. */
    public data object QrOnly : NfcMdocEngagementScope

    /**
     * NFC is the only engagement path.
     *
     * @property profile NFC handover profile exposed for the session.
     */
    public data class NfcOnly(public val profile: NfcMdocEngagementProfile) : NfcMdocEngagementScope

    /**
     * QR and NFC are prepared together and race through one shared NFC host session.
     *
     * @property profile NFC handover profile exposed for the NFC path.
     */
    public data class QrAndNfc(public val profile: NfcMdocEngagementProfile) : NfcMdocEngagementScope
}

/**
 * Complete common NFC protocol configuration; platform session details remain in the adapter.
 *
 * @property scope Engagement paths prepared for the session.
 * @property conventionalRetrieval Conventional NFC data-transfer method advertised when present;
 * NFCv2 same-channel retrieval is independent.
 */
public data class NfcMdocEngagementConfiguration(
    public val scope: NfcMdocEngagementScope,
    public val conventionalRetrieval: DeviceRetrievalMethod.Nfc? =
        DeviceRetrievalMethod.Nfc(65_535u, 65_536u),
) {
    init {
        require(scope !is NfcMdocEngagementScope.QrOnly || conventionalRetrieval != null) {
            "QR-only NFC hosting requires conventional NFC retrieval"
        }
    }
}

/**
 * NFC host owner with common selection, pruning, and exact-byte ownership.
 *
 * QR-capable scopes let the same prepared source advertise conventional NFC retrieval without
 * arming a second HCE/CardSession router.
 */
public class NfcMdocEngagementSource(
    private val configuration: NfcMdocEngagementConfiguration,
    private val platform: NfcHostPlatformAdapter,
    private val alternateTransportProviders: List<ProximityTransportProvider>,
    private val qrTransportProviders: List<ProximityTransportProvider> = emptyList(),
    private val transportCoordinator: TransportCoordinator = TransportCoordinator(),
    private val engagementFactory: MdocDeviceEngagementFactory = MdocDeviceEngagementFactory(),
) : MdocEngagementSource {
    /** Engagement modes made available by the configured scope. */
    override val modes: Set<MdocEngagementMode> = when (configuration.scope) {
        NfcMdocEngagementScope.QrOnly -> setOf(MdocEngagementMode.Qr)
        is NfcMdocEngagementScope.NfcOnly -> setOf(MdocEngagementMode.Nfc)
        is NfcMdocEngagementScope.QrAndNfc -> setOf(MdocEngagementMode.Qr, MdocEngagementMode.Nfc)
    }

    private val nfcProfile: NfcMdocEngagementProfile? = when (val scope = configuration.scope) {
        NfcMdocEngagementScope.QrOnly -> null
        is NfcMdocEngagementScope.NfcOnly -> scope.profile
        is NfcMdocEngagementScope.QrAndNfc -> scope.profile
    }

    private val includesQr: Boolean = configuration.scope !is NfcMdocEngagementScope.NfcOnly

    init {
        require(nfcProfile != null || alternateTransportProviders.isEmpty()) {
            "QR-only NFC hosting cannot prepare NFC-handover alternate transports"
        }
        require(includesQr || qrTransportProviders.isEmpty()) {
            "An NFC-only engagement source cannot prepare QR retrieval transports"
        }
    }

    /** Prepares all configured engagement paths and arms one generation-bound NFC host session. */
    override suspend fun prepare(
        context: MdocEngagementPreparationContext,
        sessionScope: CoroutineScope,
    ): PreparedMdocEngagement {
        val availability = try {
            platform.capability()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ProximityException) {
            throw failure
        } catch (failure: Throwable) {
            throw ProximityException(
                ProximityError.Capability(
                    "nfc_capability_check_failed",
                    "NFC host-card presentation capability could not be determined",
                ),
                failure,
            )
        }
        if (availability is NfcHostAvailability.Unavailable) throw ProximityException(
            ProximityError.Capability(availability.code, availability.message)
        )
        val nfcResources = PreparedTransportRegistry()
        val qrResources = PreparedTransportRegistry()
        var preparedRouter: NfcHostApduRouter? = null
        var preparedHost: PreparedNfcHostSession? = null
        try {
            val nfcContext = context.engagementContext.copy(engagementMode = MdocEngagementMode.Nfc)
            val maximumNfcSessionMessageBytes = minOf(
                nfcContext.maximumMessageBytes,
                context.limits.maximumSessionMessageBytes,
                NfcDo53.MAXIMUM_SESSION_MESSAGE_LENGTH,
            )
            val maximumNdefBytes = context.limits.maximumEngagementOrHandoverBytes.coerceAtMost(65_535)
            val ndefLimits = NdefLimits(
                maximumMessageBytes = maximumNdefBytes,
                maximumRecordPayloadBytes = maximumNdefBytes,
            )
            val readerSelectsAlternate = nfcProfile == NfcMdocEngagementProfile.Negotiated ||
                nfcProfile is NfcMdocEngagementProfile.ProvisionalV2
            val nfcDirect = configuration.conventionalRetrieval
                ?.takeIf { nfcProfile != null && nfcProfile !is NfcMdocEngagementProfile.ProvisionalV2 }
                ?.let {
                    PreparedNfcApduTransport(PreparedTransportId("nfc-handover-retrieval"), it)
                }
            val nfcAlternates = if (readerSelectsAlternate) {
                PreparedAlternateTransports(emptyList(), emptyMap())
            } else {
                prepareProviders(
                    alternateTransportProviders + listOfNotNull(nfcDirect).map(::PreparedTransportProvider),
                    nfcContext,
                    sessionScope,
                ).also {
                    nfcResources.registerAll(it.transports)
                }
            }
            val readerSelectedAlternates = if (readerSelectsAlternate) {
                inspectReaderSelectedProviders(alternateTransportProviders, nfcContext)
            } else {
                ReaderSelectedProviders(emptyList(), emptyMap())
            }
            if (readerSelectsAlternate) nfcDirect?.let { nfcResources.register(it) }
            val nfcCandidateList = nfcAlternates.transports
            val nfcCandidates = nfcCandidateList.takeIf { it.isNotEmpty() }?.let {
                PreparedTransports.of(it, nfcAlternates.unavailable + readerSelectedAlternates.unavailable)
            }
            if (nfcProfile == NfcMdocEngagementProfile.Static) {
                requireNotNull(nfcCandidates) { "Conventional NFC engagement requires a prepared retrieval method" }
            }
            if (nfcProfile == NfcMdocEngagementProfile.Negotiated) {
                require(nfcDirect != null || readerSelectedAlternates.providers.isNotEmpty()) {
                    "Negotiated NFC engagement requires an eligible reader-selected retrieval provider"
                }
            }

            val qrPath = if (includesQr) qrTransportProviders.let { providers ->
                val qrContext = context.engagementContext.copy(engagementMode = MdocEngagementMode.Qr)
                val qrDirect = configuration.conventionalRetrieval?.let {
                    PreparedNfcApduTransport(PreparedTransportId("qr-nfc-retrieval"), it)
                }
                val qrTransports = prepareProviders(
                    providers + listOfNotNull(qrDirect).map(::PreparedTransportProvider),
                    qrContext,
                    sessionScope,
                ).also { qrResources.registerAll(it.transports) }
                val qrCandidates = PreparedTransports.of(qrTransports.transports, qrTransports.unavailable)
                val qrEngagement = engagementFactory.create(
                    context.eDeviceKey,
                    qrCandidates.connectionMethods,
                    qrContext,
                    context.capabilities,
                    MdocDeviceEngagementPlacement.QR,
                )
                val exact = ImmutableBytes.of(qrEngagement.engagement.encodedCopy())
                context.limits.requireEngagementOrHandover(exact)
                PreparedQrNfcPath(
                    candidates = qrCandidates,
                    deviceEngagement = exact,
                    qrPayload = requireNotNull(qrEngagement.qrPayload),
                    directRetrieval = qrDirect,
                )
            } else null

            val conventionalEngagement = nfcCandidates
                ?.takeIf { nfcProfile == NfcMdocEngagementProfile.Static }
                ?.let { candidates ->
                    engagementFactory.create(
                        context.eDeviceKey,
                        candidates.connectionMethods,
                        nfcContext,
                        context.capabilities,
                        MdocDeviceEngagementPlacement.NFC_CONNECTION_HANDOVER,
                    )
                }
            val conventionalBytes = conventionalEngagement?.let {
                ImmutableBytes.of(it.engagement.encodedCopy()).also(context.limits::requireEngagementOrHandover)
            }
            val deviceEngagementRecord = conventionalBytes?.let(::deviceEngagementRecord)
            val exactStaticSelect = if (nfcProfile is NfcMdocEngagementProfile.Static) {
                ImmutableBytes.of(
                    NfcHandoverCodec.encodeSelect(
                        requireNotNull(nfcCandidates).transports.mapIndexed { index, transport ->
                            carrier(transport.connectionMethod, index, requireNotNull(deviceEngagementRecord))
                        },
                        ndefLimits,
                    )
                )
            } else null
            val handover = CompletableDeferred<CompletedNfcEngagement>()
            val selectedV2Candidate = CompletableDeferred<SelectedNfcV2Candidate>()
            val applicationSelection = NfcApplicationSelection(qrPath?.directRetrieval, nfcDirect)
            val engagementProcessor = when (nfcProfile) {
                null -> null
                NfcMdocEngagementProfile.Static -> NfcEngagementApduProcessor(
                    NfcEngagementConfiguration.Static(requireNotNull(exactStaticSelect)),
                    onHandover = { completed ->
                        val selected = requireNotNull(nfcCandidates)
                        applicationSelection.completeConventionalHandover(selected)
                        handover.complete(
                            CompletedNfcEngagement.Conventional(
                                completed,
                                selected,
                                requireNotNull(conventionalBytes),
                            )
                        )
                    },
                    onFailure = { failure -> handover.completeExceptionally(failure) },
                    limits = ndefLimits,
                )
                NfcMdocEngagementProfile.Negotiated -> NfcEngagementApduProcessor(
                    NfcEngagementConfiguration.Negotiated { exactRequest ->
                        val selected = selectNegotiatedCandidate(
                            exactRequest,
                            readerSelectedAlternates.providers,
                            nfcDirect,
                            nfcContext,
                            sessionScope,
                            nfcResources,
                            ndefLimits,
                        )
                        closeUnselected(listOfNotNull(nfcDirect), selected)
                        val engagement = engagementFactory.create(
                            context.eDeviceKey,
                            listOf(selected.connectionMethod),
                            nfcContext,
                            context.capabilities,
                            MdocDeviceEngagementPlacement.NFC_CONNECTION_HANDOVER,
                        )
                        val exactDeviceEngagement = ImmutableBytes.of(engagement.engagement.encodedCopy())
                            .also(context.limits::requireEngagementOrHandover)
                        val exactSelect = ImmutableBytes.of(
                            NfcHandoverCodec.encodeSelect(
                                listOf(
                                    carrier(
                                        selected.connectionMethod,
                                        0,
                                        deviceEngagementRecord(exactDeviceEngagement),
                                        omitBleUuid = (selected.connectionMethod as? DeviceRetrievalMethod.Ble)
                                            ?.centralMode != null,
                                    )
                                ),
                                ndefLimits,
                            )
                        )
                        val selectedTransports = PreparedTransports.of(
                            listOf(selected),
                            readerSelectedAlternates.unavailable,
                        )
                        applicationSelection.completeConventionalHandover(selectedTransports)
                        handover.complete(
                            CompletedNfcEngagement.Conventional(
                                NfcConnectionHandover.Negotiated(exactSelect, exactRequest),
                                selectedTransports,
                                exactDeviceEngagement,
                            )
                        )
                        exactSelect
                    },
                    onFailure = { failure -> handover.completeExceptionally(failure) },
                    limits = ndefLimits,
                )
                is NfcMdocEngagementProfile.ProvisionalV2 -> null
            }
            val retrievalProcessor = configuration.conventionalRetrieval
                ?.takeIf { nfcDirect != null || qrPath?.directRetrieval != null }
                ?.let { NfcRetrievalApduProcessor(it, maximumNfcSessionMessageBytes) }
            val nfcV2Processor = (nfcProfile as? NfcMdocEngagementProfile.ProvisionalV2)?.let { profile ->
                NfcV2ApduProcessor(
                    maximumCommandDataLength = profile.maximumCommandDataLength,
                    maximumSessionMessageBytes = maximumNfcSessionMessageBytes,
                    maximumHandoverBytes = minOf(
                        context.limits.maximumEngagementOrHandoverBytes,
                        NfcDo53.MAXIMUM_SESSION_MESSAGE_LENGTH,
                    ),
                    select = { request ->
                        val selected = selectV2Candidate(
                            request,
                            readerSelectedAlternates.providers,
                            nfcContext,
                            sessionScope,
                            nfcResources,
                        )
                        val engagement = engagementFactory.create(
                            context.eDeviceKey,
                            listOf(selected.method),
                            nfcContext,
                            context.capabilities,
                            MdocDeviceEngagementPlacement.PROVISIONAL_NFC_V2,
                        )
                        check(selectedV2Candidate.complete(selected)) {
                            "NFCv2 handover selection was already completed"
                        }
                        NfcV2HandoverSelection(selected.method, engagement.engagement)
                    },
                    onHandover = { completed ->
                        val selected = selectedV2Candidate.await()
                        val resolved = when (completed) {
                            is NfcV2Handover.SameChannel -> {
                                check(selected is SelectedNfcV2Candidate.SameChannel)
                                CompletedNfcEngagement.NfcV2SameChannel(completed)
                            }
                            is NfcV2Handover.AlternateBearer -> {
                                val alternate = selected as? SelectedNfcV2Candidate.AlternateBearer
                                    ?: error("NFCv2 alternate handover lost its prepared bearer")
                                check(alternate.method == completed.selectedMethod)
                                CompletedNfcEngagement.NfcV2AlternateBearer(completed, alternate.transport)
                            }
                        }
                        handover.complete(resolved)
                    },
                    onFailure = { failure -> handover.completeExceptionally(failure) },
                )
            }
            val router = NfcHostApduRouter(
                engagement = engagementProcessor,
                retrieval = retrievalProcessor,
                nfcV2 = nfcV2Processor,
                canSelectApplication = applicationSelection::canSelect,
                onApplicationSelected = applicationSelection::selected,
                onDeactivated = { reason ->
                    handover.completeExceptionally(
                        ProximityException(
                            ProximityError.Transport(
                                "nfc_engagement_deactivated",
                                "NFC engagement ended before a retrieval connection was established",
                            )
                        )
                    )
                    nfcDirect?.close(reason)
                    qrPath?.directRetrieval?.close(reason)
                },
            )
            preparedRouter = router
            nfcDirect?.attach(router.retrievalConnection)
            qrPath?.directRetrieval?.attach(router.retrievalConnection)
            val preparation = try {
                platform.prepare(router, sessionScope)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ProximityException) {
                throw failure
            } catch (failure: Throwable) {
                throw ProximityException(
                    ProximityError.Capability(
                        "nfc_host_prepare_failed",
                        "NFC host-card presentation could not be prepared",
                    ),
                    failure,
                )
            }
            val host = when (preparation) {
                is NfcHostPreparation.Ready -> preparation.session
                is NfcHostPreparation.Unavailable -> throw ProximityException(
                    ProximityError.Capability(
                        preparation.availability.code,
                        preparation.availability.message,
                    )
                )
            }
            preparedHost = host
            return PreparedNfcMdocEngagement(
                modes = modes,
                nfcCandidates = nfcCandidates,
                qrPath = qrPath,
                readerSelectedKinds = readerSelectedAlternates.providers.map { it.kind }.toSet(),
                nfcUnavailable = nfcAlternates.unavailable + readerSelectedAlternates.unavailable,
                handover = handover,
                router = router,
                host = host,
                transportCoordinator = transportCoordinator,
                nfcResources = nfcResources,
                qrResources = qrResources,
                sessionScope = sessionScope,
                maximumHybridMessages = context.limits.maximumExchanges.toLong() + 2L,
            )
        } catch (failure: Throwable) {
            withContext(NonCancellable) {
                runCatching { preparedHost?.close(ProximityCloseReason.CANCELLED) }
                runCatching { preparedRouter?.deactivate(ProximityCloseReason.CANCELLED) }
                nfcResources.closeAll(ProximityCloseReason.CANCELLED)
                qrResources.closeAll(ProximityCloseReason.CANCELLED)
            }
            throw failure
        }
    }

    private suspend fun prepareProviders(
        providers: List<ProximityTransportProvider>,
        context: EngagementContext,
        scope: CoroutineScope,
    ): PreparedAlternateTransports = if (providers.isEmpty()) {
        PreparedAlternateTransports(emptyList(), emptyMap())
    } else {
        transportCoordinator.prepare(providers, context, scope).let {
            PreparedAlternateTransports(it.transports, it.unavailable)
        }
    }

    private suspend fun inspectReaderSelectedProviders(
        providers: List<ProximityTransportProvider>,
        context: EngagementContext,
    ): ReaderSelectedProviders {
        require(providers.map { it.id }.distinct().size == providers.size) {
            "A transport provider identifier may be registered only once"
        }
        val eligible = mutableListOf<ReaderSelectedTransportProvider>()
        val unavailable = mutableMapOf<ProximityTransportKind, ProximityError>()
        providers.forEach { untyped ->
            val provider = untyped as? ReaderSelectedTransportProvider ?: run {
                unavailable[untyped.kind] = ProximityError.Capability(
                    "reader_selected_transport_unsupported",
                    "${untyped.kind} does not support reader-selected handover",
                )
                return@forEach
            }
            val capability = try {
                provider.capability(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                unavailable[provider.kind] = ProximityError.Capability(
                    "capability_check_failed",
                    "${provider.kind} capability check failed",
                )
                return@forEach
            }
            if (capability.mayPrepare) {
                eligible += provider
            } else {
                unavailable[provider.kind] = capability.unavailableReason ?: ProximityError.Capability(
                    "transport_unavailable",
                    "${provider.kind} is not available for the selected profile and runtime",
                )
            }
        }
        return ReaderSelectedProviders(eligible.toList(), unavailable.toMap())
    }

    private suspend fun selectNegotiatedCandidate(
        exactRequest: ImmutableBytes,
        providers: List<ReaderSelectedTransportProvider>,
        nfcDirect: PreparedNfcApduTransport?,
        context: EngagementContext,
        sessionScope: CoroutineScope,
        resources: PreparedTransportRegistry,
        ndefLimits: NdefLimits,
    ): PreparedTransport {
        val request = NfcHandoverCodec.validateRequest(exactRequest.copy(), ndefLimits)
        val offers = request.carriers.mapNotNull(NfcMdocCarrierCodec::decodeReaderOffer)
        prepareReaderSelectedCandidate(offers, providers, context, sessionScope, resources)?.let { return it }
        return nfcDirect?.takeIf { direct ->
            offers.any { offer ->
                offer is ReaderSelectedTransportOffer.Method && offer.value == direct.connectionMethod
            }
        } ?: throw IllegalArgumentException("Negotiated Handover offered no supported retrieval method")
    }

    private suspend fun selectV2Candidate(
        request: NfcV2HandoverRequest,
        providers: List<ReaderSelectedTransportProvider>,
        context: EngagementContext,
        sessionScope: CoroutineScope,
        resources: PreparedTransportRegistry,
    ): SelectedNfcV2Candidate {
        val offers = request.availableMethods
            .filterNot { it is DeviceRetrievalMethod.NfcV2 }
            .map(ReaderSelectedTransportOffer::Method)
        val alternate = prepareReaderSelectedCandidate(offers, providers, context, sessionScope, resources)
        return alternate?.let(SelectedNfcV2Candidate::AlternateBearer)
            ?: SelectedNfcV2Candidate.SameChannel(
                request.availableMethods.filterIsInstance<DeviceRetrievalMethod.NfcV2>().single(),
            )
    }

    private suspend fun prepareReaderSelectedCandidate(
        offers: List<ReaderSelectedTransportOffer>,
        providers: List<ReaderSelectedTransportProvider>,
        context: EngagementContext,
        sessionScope: CoroutineScope,
        resources: PreparedTransportRegistry,
    ): PreparedTransport? = withTimeoutOrNull(READER_SELECTED_PREPARATION_TIMEOUT) {
        for (provider in providers) {
            for (offer in offers) {
                if (!provider.acceptsReaderOffer(offer)) continue
                val prepared = try {
                    provider.prepareReaderSelected(offer, context, sessionScope)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    continue
                }
                try {
                    require(prepared.id == provider.id) {
                        "A reader-selected transport must retain its provider identifier"
                    }
                    require(prepared.kind == provider.kind) {
                        "A reader-selected transport must retain its provider kind"
                    }
                    resources.register(prepared)
                    return@withTimeoutOrNull prepared
                } catch (failure: Throwable) {
                    withContext(NonCancellable) {
                        runCatching { prepared.close(ProximityCloseReason.CANCELLED) }
                    }
                    throw failure
                }
            }
        }
        null
    }

    private suspend fun closeUnselected(candidates: List<PreparedTransport>, selected: PreparedTransport?) {
        withContext(NonCancellable) {
            candidates.filterNot { it === selected }.forEach {
                runCatching { it.close(ProximityCloseReason.LOST_RACE) }
            }
        }
    }

    private fun carrier(
        method: DeviceRetrievalMethod,
        index: Int,
        deviceEngagementRecord: NdefRecord,
        omitBleUuid: Boolean = false,
    ): NfcHandoverCarrier = NfcMdocCarrierCodec.encode(
        method = method,
        carrierReference = ImmutableBytes.of(index.toString().encodeToByteArray()),
        auxiliaryRecords = listOf(deviceEngagementRecord),
        actor = NfcMdocActor.HOLDER,
        omitBleUuid = omitBleUuid,
    )

    private fun deviceEngagementRecord(exact: ImmutableBytes): NdefRecord = NdefRecord(
        typeNameFormat = NdefTypeNameFormat.EXTERNAL,
        type = ImmutableBytes.of("iso.org:18013:deviceengagement".encodeToByteArray()),
        identifier = ImmutableBytes.of("mdoc".encodeToByteArray()),
        payload = exact,
    )

    private companion object {
        /**
         * Reader-selected endpoint setup runs while the NFC reader is waiting for Hs. This is
         * intentionally much shorter than a normal bearer connection timeout: preparation may
         * publish or initialize an endpoint, but it must never wait for the peer to connect.
         */
        val READER_SELECTED_PREPARATION_TIMEOUT = 2.seconds
    }
}

private sealed interface CompletedNfcEngagement {
    data class Conventional(
        val handover: NfcConnectionHandover,
        val selected: PreparedTransports,
        val deviceEngagement: ImmutableBytes,
    ) : CompletedNfcEngagement

    data class NfcV2SameChannel(
        val handover: NfcV2Handover.SameChannel,
    ) : CompletedNfcEngagement

    data class NfcV2AlternateBearer(
        val handover: NfcV2Handover.AlternateBearer,
        val selected: PreparedTransport,
    ) : CompletedNfcEngagement
}

private sealed interface SelectedNfcV2Candidate {
    val method: DeviceRetrievalMethod

    data class SameChannel(
        override val method: DeviceRetrievalMethod.NfcV2,
    ) : SelectedNfcV2Candidate

    class AlternateBearer(val transport: PreparedTransport) : SelectedNfcV2Candidate {
        override val method: DeviceRetrievalMethod = transport.connectionMethod

        init {
            require(method !is DeviceRetrievalMethod.NfcV2) {
                "A prepared NFCv2 alternate bearer cannot represent the same APDU channel"
            }
        }
    }
}

private data class PreparedQrNfcPath(
    val candidates: PreparedTransports,
    val deviceEngagement: ImmutableBytes,
    val qrPayload: String,
    val directRetrieval: PreparedNfcApduTransport?,
)

/** Resolves one DATA_TRANSFER selection to the transcript path that made it legal. */
private class NfcApplicationSelection(
    private val qrDirect: PreparedNfcApduTransport?,
    private val nfcDirect: PreparedNfcApduTransport?,
) {
    private val mutex = Mutex()
    private var selectedEngagement: NfcHostApplication? = null
    private var nfcDirectEligible = false
    private var activated: PreparedNfcApduTransport? = null

    suspend fun completeConventionalHandover(selected: PreparedTransports) {
        mutex.withLock { nfcDirectEligible = nfcDirect != null && nfcDirect in selected.transports }
    }

    suspend fun canSelect(application: NfcHostApplication): Boolean = mutex.withLock {
        when (application) {
            NfcHostApplication.ENGAGEMENT,
            NfcHostApplication.NFC_V2 ->
                activated == null && (selectedEngagement == null || selectedEngagement == application)
            NfcHostApplication.RETRIEVAL ->
                activated != null ||
                    (selectedEngagement != null && nfcDirectEligible && nfcDirect != null) ||
                    (selectedEngagement == null && qrDirect != null)
        }
    }

    suspend fun selected(application: NfcHostApplication): Unit = mutex.withLock {
        when (application) {
            NfcHostApplication.ENGAGEMENT,
            NfcHostApplication.NFC_V2 -> {
                check(activated == null) { "An engagement application cannot replace direct QR retrieval" }
                check(selectedEngagement == null || selectedEngagement == application) {
                    "An NFC engagement application cannot be replaced in one session"
                }
                selectedEngagement = application
            }
            NfcHostApplication.RETRIEVAL -> {
                if (activated != null) return@withLock
                val selected = when {
                    selectedEngagement != null && nfcDirectEligible -> nfcDirect
                    selectedEngagement == null -> qrDirect
                    else -> null
                } ?: error("An ineligible NFC retrieval application was selected")
                activated = selected
                selected.activate()
            }
        }
    }
}

private class PreparedNfcMdocEngagement(
    override val modes: Set<MdocEngagementMode>,
    nfcCandidates: PreparedTransports?,
    private val qrPath: PreparedQrNfcPath?,
    readerSelectedKinds: Set<ProximityTransportKind>,
    nfcUnavailable: Map<ProximityTransportKind, ProximityError>,
    private val handover: CompletableDeferred<CompletedNfcEngagement>,
    private val router: NfcHostApduRouter,
    private val host: PreparedNfcHostSession,
    private val transportCoordinator: TransportCoordinator,
    private val nfcResources: PreparedTransportRegistry,
    private val qrResources: PreparedTransportRegistry,
    private val sessionScope: CoroutineScope,
    private val maximumHybridMessages: Long,
) : PreparedMdocEngagement {
    private val availableTransportKinds = (
            nfcCandidates?.transports.orEmpty() + qrPath?.candidates?.transports.orEmpty()
        ).map { it.kind }.toSet() + readerSelectedKinds + ProximityTransportKind.NFC
    override val readiness: MdocEngagementReadiness = MdocEngagementReadiness(
        qrPayload = qrPath?.qrPayload,
        availableTransports = availableTransportKinds,
        unavailableTransports = (nfcUnavailable + qrPath?.candidates?.unavailable.orEmpty())
            .filterKeys { it !in availableTransportKinds },
    )
    private val closeMutex = Mutex()
    private var closed = false
    private var activeConnection: ProximityConnection? = null

    override suspend fun awaitConnection(): MdocEngagedConnection {
        val engaged = if (qrPath == null) {
            awaitNfcConnection()
        } else {
            awaitQrOrNfcConnection(qrPath)
        }
        val closeImmediately = closeMutex.withLock {
            if (closed) true else {
                activeConnection = engaged.connection
                false
            }
        }
        if (closeImmediately) {
            engaged.connection.close(ProximityCloseReason.CANCELLED)
            throw ProximityException(
                ProximityError.Transport("engagement_closed", "NFC engagement closed before connection delivery")
            )
        }
        if (
            engaged.sessionHandover is MdocSessionHandover.NfcConnection &&
            engaged.connection.kind != ProximityTransportKind.NFC
        ) {
            releaseNfcHost(ProximityCloseReason.HANDOVER_COMPLETED)
        }
        return engaged
    }

    private suspend fun awaitQrOrNfcConnection(qr: PreparedQrNfcPath): MdocEngagedConnection = supervisorScope {
        val results = Channel<Result<MdocEngagedConnection>>(2)
        val jobs = listOf(
            launch { results.send(runCatching { awaitQrConnection(qr) }) },
            launch { results.send(runCatching { awaitNfcConnection() }) },
        )
        try {
            var failures = 0
            var winner: MdocEngagedConnection? = null
            while (winner == null && failures < jobs.size) {
                results.receive().onSuccess { winner = it }.onFailure { failures++ }
            }
            val selected = winner ?: throw ProximityException(
                ProximityError.Transport("engagement_failed", "QR and NFC engagement both failed")
            )
            jobs.forEach { if (it.isActive) it.cancelAndJoin() }
            if (selected.engagementMode == MdocEngagementMode.Qr) {
                nfcResources.closeAll(ProximityCloseReason.LOST_RACE)
                if (selected.connection.kind != ProximityTransportKind.NFC) {
                    releaseNfcHost(ProximityCloseReason.LOST_RACE)
                }
            } else {
                qrResources.closeAll(ProximityCloseReason.LOST_RACE)
            }
            selected
        } catch (failure: Throwable) {
            withContext(NonCancellable) { jobs.forEach { if (it.isActive) it.cancelAndJoin() } }
            throw failure
        } finally {
            results.close()
        }
    }

    private suspend fun awaitQrConnection(qr: PreparedQrNfcPath): MdocEngagedConnection {
        val winner = transportCoordinator.awaitWinner(qr.candidates)
        return MdocEngagedConnection(
            engagementMode = MdocEngagementMode.Qr,
            deviceEngagement = qr.deviceEngagement,
            sessionHandover = MdocSessionHandover.Qr,
            connection = winner.connection,
        )
    }

    private suspend fun awaitNfcConnection(): MdocEngagedConnection {
        val engaged = when (val completed = handover.await()) {
            is CompletedNfcEngagement.Conventional -> {
                val winner = transportCoordinator.awaitWinner(completed.selected)
                val exact = completed.handover
                MdocEngagedConnection(
                    engagementMode = MdocEngagementMode.Nfc,
                    deviceEngagement = completed.deviceEngagement,
                    sessionHandover = MdocSessionHandover.NfcConnection(
                        exact.handoverSelect,
                        (exact as? NfcConnectionHandover.Negotiated)?.handoverRequest,
                    ),
                    connection = winner.connection,
                )
            }
            is CompletedNfcEngagement.NfcV2SameChannel -> {
                MdocEngagedConnection(
                    engagementMode = MdocEngagementMode.Nfc,
                    deviceEngagement = ImmutableBytes.of(completed.handover.deviceEngagement.encodedCopy()),
                    sessionHandover = MdocSessionHandover.ProvisionalNfcV2(
                        completed.handover.handoverSelect,
                        completed.handover.handoverRequest,
                    ),
                    connection = router.nfcV2Connection,
                )
            }
            is CompletedNfcEngagement.NfcV2AlternateBearer -> {
                MdocEngagedConnection(
                    engagementMode = MdocEngagementMode.Nfc,
                    deviceEngagement = ImmutableBytes.of(completed.handover.deviceEngagement.encodedCopy()),
                    sessionHandover = MdocSessionHandover.ProvisionalNfcV2(
                        completed.handover.handoverSelect,
                        completed.handover.handoverRequest,
                    ),
                    connection = NfcV2HybridProximityConnection(
                        nfc = router.nfcV2Connection,
                        alternate = completed.selected,
                        sessionScope = sessionScope,
                        maximumMessagesPerDirection = maximumHybridMessages,
                    ),
                )
            }
        }
        return engaged
    }

    private suspend fun releaseNfcHost(reason: ProximityCloseReason) = withContext(NonCancellable) {
        try {
            host.close(reason)
        } finally {
            router.deactivate(reason)
        }
    }

    override suspend fun close(reason: ProximityCloseReason) {
        val connection = closeMutex.withLock {
            if (closed) return
            closed = true
            activeConnection.also { activeConnection = null }
        }
        withContext(NonCancellable) {
            try {
                connection?.close(reason)
            } finally {
                try {
                    releaseNfcHost(reason)
                } finally {
                    nfcResources.closeAll(reason)
                    qrResources.closeAll(reason)
                }
            }
        }
    }
}

/** Lets the shared coordinator include an already-created local bearer in one preparation decision. */
private class PreparedTransportProvider(
    private val transport: PreparedTransport,
) : ProximityTransportProvider {
    override val kind: ProximityTransportKind = transport.kind
    override val id: PreparedTransportId = transport.id

    override suspend fun capability(context: EngagementContext): ProximityCapability =
        ProximityCapability(
            implemented = true,
            profilePermitted = true,
            runtimeAvailable = true,
            sessionSelected = true,
        )

    override suspend fun prepare(
        context: EngagementContext,
        sessionScope: CoroutineScope,
    ): PreparedTransport = transport
}

private class PreparedNfcApduTransport(
    override val id: PreparedTransportId,
    override val connectionMethod: DeviceRetrievalMethod.Nfc,
) : PreparedTransport {
    override val kind: ProximityTransportKind = ProximityTransportKind.NFC
    private val activated = CompletableDeferred<Unit>()
    private lateinit var connection: ProximityConnection

    fun attach(connection: ProximityConnection) {
        check(!::connection.isInitialized)
        this.connection = connection
    }

    fun activate() {
        activated.complete(Unit)
    }

    override suspend fun awaitConnection(): ProximityConnection {
        activated.await()
        return connection
    }

    override suspend fun close(reason: ProximityCloseReason) {
        activated.completeExceptionally(
            ProximityException(
                ProximityError.Transport(
                    "nfc_retrieval_closed",
                    "NFC retrieval ended before the reader selected the data-transfer application",
                )
            )
        )
    }
}

private data class PreparedAlternateTransports(
    val transports: List<PreparedTransport>,
    val unavailable: Map<ProximityTransportKind, ProximityError>,
)

private data class ReaderSelectedProviders(
    val providers: List<ReaderSelectedTransportProvider>,
    val unavailable: Map<ProximityTransportKind, ProximityError>,
)

/** Owns every transport prepared before or during one handover and closes it exactly once by id. */
private class PreparedTransportRegistry {
    private val mutex = Mutex()
    private val transports = linkedMapOf<PreparedTransportId, PreparedTransport>()
    private var closed = false

    suspend fun register(transport: PreparedTransport) {
        val accepted = mutex.withLock {
            if (closed) return@withLock false
            require(transport.id !in transports) {
                "A prepared transport identifier may be registered only once"
            }
            transports[transport.id] = transport
            true
        }
        if (!accepted) {
            withContext(NonCancellable) {
                runCatching { transport.close(ProximityCloseReason.CANCELLED) }
            }
            throw IllegalStateException("The prepared-transport registry is already closed")
        }
    }

    suspend fun registerAll(values: Collection<PreparedTransport>) {
        values.forEach { register(it) }
    }

    suspend fun closeAll(reason: ProximityCloseReason) {
        val owned = mutex.withLock {
            if (closed) return
            closed = true
            transports.values.toList().also { transports.clear() }
        }
        withContext(NonCancellable) {
            owned.forEach { transport -> runCatching { transport.close(reason) } }
        }
    }
}
