@file:OptIn(
    ExperimentalSerializationApi::class,
    ExperimentalCoroutinesApi::class,
    ExperimentalUnsignedTypes::class,
)

package id.walt.mdoc.proximity

import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.toPublicJwk
import id.walt.mdoc.crypto.MdocCryptoHelper
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.encoding.ExactCbor
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.engagement.BlePeripheralEndpoint
import id.walt.mdoc.objects.engagement.DeviceEngagement
import id.walt.mdoc.objects.engagement.DeviceEngagementSecurity
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.objects.session.SessionData
import id.walt.mdoc.objects.session.SessionEstablishment
import id.walt.mdoc.objects.session.SessionStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.io.encoding.Base64
import kotlin.time.Duration

data class MdocEngagement(
    val engagement: ExactCbor<DeviceEngagement>,
    val qrPayload: String?,
)

/** Exact placement rules for retrieval methods and QR encoding in one Device Engagement. */
enum class MdocDeviceEngagementPlacement {
    QR,
    NFC_CONNECTION_HANDOVER,
    PROVISIONAL_NFC_V2,
}

class MdocDeviceEngagementFactory {
    /** Encodes the complete `EDeviceKeyBytes = #6.24(bstr .cbor EDeviceKey)` value. */
    suspend fun encodeEDeviceKeyBytes(eDeviceKey: Key): ImmutableBytes =
        ImmutableBytes.of(
            coseCompliantCbor.encodeToByteArray(
                CborElement.serializer(),
                CborByteString(encodePublicDeviceKey(eDeviceKey).encoded, 24u),
            )
        )

    suspend fun create(
        eDeviceKey: Key,
        methods: List<DeviceRetrievalMethod>,
        context: EngagementContext,
        capabilities: MdocSessionCapabilities,
        placement: MdocDeviceEngagementPlacement = if (context.engagementMode is MdocEngagementMode.Qr) {
            MdocDeviceEngagementPlacement.QR
        } else {
            MdocDeviceEngagementPlacement.NFC_CONNECTION_HANDOVER
        },
    ): MdocEngagement {
        val methods = methods.map { it.snapshot() }
        require(methods.isNotEmpty()) { "At least one retrieval method is required" }
        require(capabilities.profile == context.profile) { "Capability profile must match the engagement profile" }
        require(eDeviceKey.spec.toMdocSessionCurve() == capabilities.selectedCurve) {
            "Selected session curve does not match the ephemeral device key"
        }
        val provisionalOnlyMethod = methods.firstOrNull { method ->
            method is DeviceRetrievalMethod.NfcV2 ||
                method is DeviceRetrievalMethod.Ble && method.peripheralEndpoint is BlePeripheralEndpoint.Reader
        }
        require(provisionalOnlyMethod == null || placement == MdocDeviceEngagementPlacement.PROVISIONAL_NFC_V2) {
            "NFCv2 and reader-owned BLE endpoints require provisional NFCv2 placement"
        }
        require(placement != MdocDeviceEngagementPlacement.PROVISIONAL_NFC_V2 || methods.size == 1) {
            "Provisional NFCv2 Device Engagement must select exactly one retrieval method"
        }
        val (publicCose, encodedCose) = encodePublicDeviceKey(eDeviceKey)
        val engagementCapabilities = capabilities.toDeviceEngagementCapabilities()
        val usesEdition2Fields = engagementCapabilities != null
        val engagement = DeviceEngagement(
            version = if (usesEdition2Fields) DeviceEngagement.VERSION_1_1 else DeviceEngagement.VERSION_1_0,
            security = DeviceEngagementSecurity(1u, ByteStringWrapper(publicCose, encodedCose)),
            deviceRetrievalMethods = methods.toList().takeIf {
                placement != MdocDeviceEngagementPlacement.NFC_CONNECTION_HANDOVER
            },
            originInfos = emptyList<CborElement>().takeIf { usesEdition2Fields },
            capabilities = engagementCapabilities,
        )
        val exact = ExactCbor.of(
            engagement,
            coseCompliantCbor.encodeToByteArray(DeviceEngagement.serializer(), engagement),
        )
        val qrPayload = if (placement == MdocDeviceEngagementPlacement.QR) {
            "mdoc:" + Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(exact.encodedCopy())
        } else null
        return MdocEngagement(exact, qrPayload)
    }

    private suspend fun encodePublicDeviceKey(eDeviceKey: Key): EncodedDeviceKey {
        MdocSessionKeyValidator.requireSupportedLocalKey(eDeviceKey)
        val publicJwk = requireNotNull(eDeviceKey.capabilities.publicKeyExporter) {
            "Ephemeral device key cannot export public material"
        }.exportPublicKey().toPublicJwk(eDeviceKey.spec)
        val publicCose = publicJwk.toCoseKey()
        return EncodedDeviceKey(
            publicCose,
            coseCompliantCbor.encodeToByteArray(CoseKey.serializer(), publicCose),
        )
    }

    private data class EncodedDeviceKey(val coseKey: CoseKey, val encoded: ByteArray)
}

data class PreviewElement(
    val namespace: String,
    val elementIdentifier: String,
    val intentToRetain: Boolean,
) {
    init {
        require(namespace.isNotBlank() && elementIdentifier.isNotBlank())
    }
}

class PreviewDocument(
    val docType: String,
    credentialIds: List<String>,
    elements: List<PreviewElement>,
) {
    private val ownedCredentialIds: List<String> = credentialIds.toList()
    val credentialIds: List<String> get() = ownedCredentialIds.toList()
    private val ownedElements: List<PreviewElement> = elements.toList()
    val elements: List<PreviewElement> get() = ownedElements.toList()

    init {
        require(docType.isNotBlank() && this.credentialIds.isNotEmpty() && this.elements.isNotEmpty())
        require(this.credentialIds.none(String::isBlank) && this.credentialIds.distinct().size == this.credentialIds.size)
        require(this.elements.distinctBy { it.namespace to it.elementIdentifier }.size == this.elements.size)
    }
}

class MdocRequestPreview(
    documents: List<PreviewDocument>,
    purposeHints: Map<String, Int> = emptyMap(),
    val readerAuthentication: DeviceRequestReaderAuthenticationDisplay? = null,
    /**
     * SHA-256 digest over the wallet-owned trust snapshot, eligible credentials, selected use-case/elements,
     * retention flags, authentication method, holder-key reference, validated application-profile results,
     * and selected device-signed response mappings.
     */
    val submissionBindingDigest: ImmutableBytes,
    applicationAuthorizations: List<MdocApplicationAuthorization> = emptyList(),
) {
    private val ownedDocuments: List<PreviewDocument> = documents.toList()
    val documents: List<PreviewDocument> get() = ownedDocuments.toList()
    private val ownedPurposeHints: Map<String, Int> = purposeHints.toMap()
    val purposeHints: Map<String, Int> get() = ownedPurposeHints.toMap()
    /** Wallet-profile results already validated and normalized for display during holder consent. */
    private val ownedApplicationAuthorizations: List<MdocApplicationAuthorization> = applicationAuthorizations.toList()
    val applicationAuthorizations: List<MdocApplicationAuthorization> get() = ownedApplicationAuthorizations.toList()

    init {
        require(this.documents.isNotEmpty()) { "A request preview must contain at least one document" }
        require(submissionBindingDigest.size == SHA256_BYTES) { "Submission binding must be a SHA-256 digest" }
    }

    private companion object { const val SHA256_BYTES = 32 }
}

/** Exact protocol inputs remain owned even when a processor inspects or modifies a decoded projection. */
class MdocHolderRequestContext(
    request: ExactCbor<DeviceRequest>,
    transcript: ExactCbor<SessionTranscript>,
    readerEphemeralKey: ExactCbor<CoseKey>,
    val exchange: Int,
) {
    private val requestBytes = request.encodedCopy()
    private val transcriptBytes = transcript.encodedCopy()
    private val readerKeyBytes = readerEphemeralKey.encodedCopy()

    val request: ExactCbor<DeviceRequest>
        get() = ExactCbor.of(coseCompliantCbor.decodeFromByteArray(requestBytes), requestBytes)
    /** Exact SessionTranscriptBytes, including the tag-24 byte-string wrapper. */
    val transcript: ExactCbor<SessionTranscript>
        get() = ExactCbor.of(
            coseCompliantCbor.decodeFromByteArray(coseCompliantCbor.decodeFromByteArray<ByteArray>(transcriptBytes)),
            transcriptBytes,
        )
    /** Exact reader ephemeral COSE_Key from SessionEstablishment, available for device-MAC selection. */
    val readerEphemeralKey: ExactCbor<CoseKey>
        get() = ExactCbor.of(coseCompliantCbor.decodeFromByteArray(readerKeyBytes), readerKeyBytes)

    init { require(exchange > 0) }
}

enum class MdocSessionContinuation { CONTINUE, TERMINATE }

sealed interface MdocResponseResolution {
    /** Freshly recomputed wallet-owned binding; must still equal the preview binding before submission. */
    val submissionBindingDigest: ImmutableBytes

    data class Send(
        val exactResponse: ImmutableBytes,
        val continuation: MdocSessionContinuation,
        override val submissionBindingDigest: ImmutableBytes,
    ) : MdocResponseResolution

    data class TerminateWithoutResponse(
        override val submissionBindingDigest: ImmutableBytes,
    ) : MdocResponseResolution
}

sealed interface MdocRequestPreparation {
    data class Review(val preview: MdocRequestPreview) : MdocRequestPreparation
    /** A valid request has no returnable data; no holder consent or key authorization is needed. */
    data object NoData : MdocRequestPreparation
    /** Policy rejected disclosure; retain the local failure while returning no credential data. */
    data class Rejected(val error: ProximityError) : MdocRequestPreparation
}

interface MdocHolderRequestProcessor {
    suspend fun prepare(context: MdocHolderRequestContext): MdocRequestPreparation =
        MdocRequestPreparation.Review(preview(context))
    suspend fun preview(context: MdocHolderRequestContext): MdocRequestPreview
    suspend fun resolve(
        context: MdocHolderRequestContext,
        preview: MdocRequestPreview,
    ): MdocResponseResolution
}

data class MdocConsentPrompt(
    val bindingToken: ImmutableBytes,
    val exchange: Int,
    val preview: MdocRequestPreview,
) {
    init {
        require(bindingToken.size == 32) { "Consent binding must be a SHA-256 digest" }
        require(exchange > 0)
    }
}

sealed interface MdocConsentDecision {
    val bindingToken: ImmutableBytes
    data class Approve(override val bindingToken: ImmutableBytes) : MdocConsentDecision
    data class Deny(override val bindingToken: ImmutableBytes) : MdocConsentDecision
}

fun interface MdocConsentHandler {
    suspend fun decide(prompt: MdocConsentPrompt): MdocConsentDecision
}

sealed interface MdocHolderSessionState {
    data object Idle : MdocHolderSessionState
    data class Preparing(val profileId: String) : MdocHolderSessionState
    data class EngagementReady(
        val qrPayload: String?,
        val engagementModes: Set<MdocEngagementMode>,
        val availableTransports: Set<ProximityTransportKind>,
        val unavailableTransports: Map<ProximityTransportKind, ProximityError>,
    ) : MdocHolderSessionState {
        init { require(engagementModes.isNotEmpty()) }
    }
    data class Connecting(
        val qrPayload: String?,
        val engagementModes: Set<MdocEngagementMode>,
        val availableTransports: Set<ProximityTransportKind>,
        val unavailableTransports: Map<ProximityTransportKind, ProximityError>,
    ) : MdocHolderSessionState {
        init { require(engagementModes.isNotEmpty()) }
    }
    data class AwaitingRequest(val exchange: Int) : MdocHolderSessionState {
        init { require(exchange > 0) }
    }
    data class ReviewRequired(val prompt: MdocConsentPrompt) : MdocHolderSessionState
    data class SendingResponse(val exchange: Int) : MdocHolderSessionState {
        init { require(exchange > 0) }
    }
    data class AwaitingNextRequest(val completedExchanges: Int) : MdocHolderSessionState {
        init { require(completedExchanges > 0) }
    }
    data class Terminating(val exchange: Int) : MdocHolderSessionState {
        init { require(exchange > 0) }
    }
    data class Declined(val exchange: Int) : MdocHolderSessionState {
        init { require(exchange > 0) }
    }
    /** The final request ended without disclosure; earlier exchanges may have sent approved data. */
    data class NoData(val exchange: Int) : MdocHolderSessionState {
        init { require(exchange > 0) }
    }
    data class Completed(val exchanges: Int) : MdocHolderSessionState {
        init { require(exchanges > 0) }
    }
    data class Failed(val error: ProximityError) : MdocHolderSessionState
    data object Cancelled : MdocHolderSessionState
}

enum class MdocHolderAction { CANCEL, APPROVE, DENY }

/** Legal user actions for a display-safe session state. */
val MdocHolderSessionState.legalActions: Set<MdocHolderAction>
    get() = when (this) {
        MdocHolderSessionState.Idle,
        is MdocHolderSessionState.Declined,
        is MdocHolderSessionState.Completed,
        is MdocHolderSessionState.NoData,
        is MdocHolderSessionState.Failed,
        is MdocHolderSessionState.Terminating,
        MdocHolderSessionState.Cancelled -> emptySet()
        is MdocHolderSessionState.ReviewRequired -> setOf(
            MdocHolderAction.APPROVE,
            MdocHolderAction.DENY,
            MdocHolderAction.CANCEL,
        )
        is MdocHolderSessionState.Preparing,
        is MdocHolderSessionState.EngagementReady,
        is MdocHolderSessionState.Connecting,
        is MdocHolderSessionState.AwaitingRequest,
        is MdocHolderSessionState.AwaitingNextRequest,
        is MdocHolderSessionState.SendingResponse -> setOf(MdocHolderAction.CANCEL)
    }

sealed interface MdocHolderSessionResult {
    data class Declined(val exchange: Int) : MdocHolderSessionResult
    /** No data was disclosed for the final request; this does not describe earlier exchanges. */
    data class NoData(val exchange: Int) : MdocHolderSessionResult {
        init { require(exchange > 0) }
    }
    data class Completed(val exchanges: Int) : MdocHolderSessionResult
    data class Failed(val error: ProximityError) : MdocHolderSessionResult
}

/**
 * Radio-independent holder state machine. Platform adapters only prepare transports and move complete messages.
 */
class MdocHolderProtocolEngine(
    private val eDeviceKey: Key,
    engagementSources: List<MdocEngagementSource>,
    private val requestProcessor: MdocHolderRequestProcessor,
    private val consentHandler: MdocConsentHandler,
    private val engagementContext: EngagementContext,
    private val capabilities: MdocSessionCapabilities,
    private val limits: MdocProximityLimits = MdocProximityLimits(),
    private val timeouts: MdocProximityTimeouts = MdocProximityTimeouts(),
    private val engagementCoordinator: MdocEngagementCoordinator = MdocEngagementCoordinator(),
) {
    private val engagementSources = engagementSources.toList()
    private val mutableState = MutableStateFlow<MdocHolderSessionState>(MdocHolderSessionState.Idle)
    val state: StateFlow<MdocHolderSessionState> = mutableState.asStateFlow()
    private val startMutex = Mutex()
    private var started = false
    private lateinit var messageSequencer: MdocSessionMessageSequencer

    init {
        require(capabilities.profile == engagementContext.profile) {
            "Capability profile must match the engagement profile"
        }
        require(engagementSources.isNotEmpty()) { "At least one engagement source is required" }
        require(engagementSources.all { it.modes.isNotEmpty() }) {
            "An engagement source must own at least one mode"
        }
        require(
            engagementSources.flatMap { it.modes }.distinct().size == engagementSources.sumOf { it.modes.size }
        ) {
            "An engagement mode may be configured by only one source"
        }
    }

    /**
     * Runs this single-use session until completion, decline, or failure.
     *
     * Caller cancellation is rethrown after the state becomes [MdocHolderSessionState.Cancelled];
     * prepared transports, the active connection, and session keys are then closed before return.
     */
    suspend fun run(): MdocHolderSessionResult {
        startMutex.withLock {
            check(!started) { "An mdoc holder protocol engine is single-use" }
            started = true
        }
        return try {
            withTotalSessionTimeout()
        } catch (cancelled: CancellationException) {
            mutableState.value = MdocHolderSessionState.Cancelled
            throw cancelled
        } catch (failure: ProximityException) {
            mutableState.value = MdocHolderSessionState.Failed(failure.error)
            MdocHolderSessionResult.Failed(failure.error)
        } catch (failure: Exception) {
            val error = ProximityError.Protocol("session_failed", "The proximity session failed")
            mutableState.value = MdocHolderSessionState.Failed(error)
            MdocHolderSessionResult.Failed(error)
        }
    }

    private suspend fun withTotalSessionTimeout(): MdocHolderSessionResult = phase(
        timeouts.totalSession,
        ProximityError.Protocol("session_timeout", "The proximity session exceeded its time limit"),
    ) { coroutineScope { runSession(this) } }

    private suspend fun runSession(scope: CoroutineScope): MdocHolderSessionResult {
        mutableState.value = MdocHolderSessionState.Preparing(engagementContext.profile.id)
        var prepared: PreparedMdocEngagements? = null
        var winningSource: PreparedMdocEngagement? = null
        var cipher: MdocSessionCipher? = null
        var closeReason = ProximityCloseReason.PROTOCOL_ERROR
        val budget = MdocSessionBudget(limits.maximumCumulativeSessionBytes)
        try {
            prepared = engagementCoordinator.prepare(
                sources = engagementSources,
                context = MdocEngagementPreparationContext(
                    eDeviceKey = eDeviceKey,
                    engagementContext = engagementContext,
                    capabilities = capabilities,
                    limits = limits,
                ),
                sessionScope = scope,
            )
            val readiness = prepared.readiness
            val engagementModes = prepared.sources.flatMap { it.modes }.toSet()
            mutableState.value = MdocHolderSessionState.EngagementReady(
                readiness.qrPayload,
                engagementModes,
                readiness.availableTransports,
                readiness.unavailableTransports,
            )
            val (winner, firstBytes) = if (readiness.qrPayload != null) {
                phase(
                    timeouts.qrEngagementLifetime,
                    ProximityError.Protocol("engagement_timeout", "The QR engagement expired before session establishment"),
                ) {
                    connectAndReceiveEstablishment(prepared, budget) {
                        mutableState.value = MdocHolderSessionState.Connecting(
                            readiness.qrPayload,
                            engagementModes,
                            readiness.availableTransports,
                            readiness.unavailableTransports,
                        )
                    }
                }
            } else {
                connectAndReceiveEstablishment(prepared, budget) {
                    mutableState.value = MdocHolderSessionState.Connecting(
                        readiness.qrPayload,
                        engagementModes,
                        readiness.availableTransports,
                        readiness.unavailableTransports,
                    )
                }
            }
            winningSource = winner.source
            val engaged = winner.engaged
            val connection = engaged.connection
            val engagementBytes = engaged.deviceEngagement
            val sessionHandover = engaged.sessionHandover
            limits.requireEngagementOrHandover(engagementBytes)
            when (sessionHandover) {
                MdocSessionHandover.Qr -> Unit
                is MdocSessionHandover.NfcConnection -> {
                    limits.requireEngagementOrHandover(sessionHandover.handoverSelect)
                    sessionHandover.handoverRequest?.let(limits::requireEngagementOrHandover)
                }
                is MdocSessionHandover.ProvisionalNfcV2 -> {
                    limits.requireEngagementOrHandover(sessionHandover.handoverSelect)
                    limits.requireEngagementOrHandover(sessionHandover.handoverRequest)
                }
            }
            messageSequencer = MdocSessionMessageSequencer(sessionHandover.sessionMessageProfile)
            val establishment = decodeEstablishmentOrReport(connection, firstBytes)
            messageSequencer.validateIncoming(establishment)
            val transcript = sessionHandover.createTranscript(
                engagementBytes,
                ImmutableBytes.of(establishment.eReaderKey.serialized),
            )
            val transcriptBytes = MdocCryptoHelper.buildSessionTranscriptBytes(transcript)
            val exactTranscript = ImmutableBytes.of(transcriptBytes)
            limits.requireEngagementOrHandover(exactTranscript)
            MdocCborGuard.validate(transcriptBytes, limits.maximumCborDepth, limits.maximumCborItems)
            cipher = try {
                MdocSessionCipher.establishForHolder(eDeviceKey, establishment.eReaderKey.value, transcriptBytes)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                // WebCrypto can reject malformed peer keys with a native JavaScript error.
                trySendStatus(connection, SessionStatusCode.SESSION_ENCRYPTION_ERROR)
                throw ProximityException(ProximityError.Security("invalid_reader_key", "Reader session key is invalid"), failure)
            }

            var incoming = decryptOrReport(connection, cipher, ImmutableBytes.of(establishment.data))
            var terminateAfterResponse = false
            var exchange = 0
            while (true) {
                exchange++
                if (exchange > limits.maximumExchanges) throw ProximityException(
                    ProximityError.Protocol("exchange_limit", "The session exceeded the configured exchange limit")
                )
                mutableState.value = MdocHolderSessionState.AwaitingRequest(exchange)
                limits.requireRequest(incoming)
                val request = decodeRequestOrReport(connection, cipher, incoming, budget)
                val context = MdocHolderRequestContext(
                    request = ExactCbor.of(request, incoming.copy()),
                    transcript = ExactCbor.of(transcript, exactTranscript.copy()),
                    readerEphemeralKey = ExactCbor.of(
                        establishment.eReaderKey.value,
                        establishment.eReaderKey.serialized,
                    ),
                    exchange = exchange,
                )
                val preparation = phase(
                    timeouts.request,
                    ProximityError.Protocol("request_processing_timeout", "Request preview processing timed out"),
                ) { whileConnected(connection) { requestProcessor.prepare(context) } }
                if (preparation !is MdocRequestPreparation.Review) {
                    mutableState.value = MdocHolderSessionState.Terminating(exchange)
                    phase(timeouts.gracefulTermination, ProximityError.Transport("termination_timeout", "Session termination timed out")) {
                        sendEmptyResponse(connection, cipher, 0u, budget)
                    }
                    if (preparation is MdocRequestPreparation.Rejected) throw ProximityException(preparation.error)
                    closeReason = ProximityCloseReason.COMPLETED
                    mutableState.value = MdocHolderSessionState.NoData(exchange)
                    return MdocHolderSessionResult.NoData(exchange)
                }
                val preview = preparation.preview
                val token = consentBinding(incoming, exactTranscript, exchange, preview)
                val prompt = MdocConsentPrompt(token, exchange, preview)
                mutableState.value = MdocHolderSessionState.ReviewRequired(prompt)
                val decision = phase(
                    timeouts.consent,
                    ProximityError.Policy("consent_timeout", "Holder consent timed out"),
                ) { whileConnected(connection) { consentHandler.decide(prompt) } }
                if (decision.bindingToken != token) throw ProximityException(
                    ProximityError.Security("stale_consent", "Consent does not belong to the active request preview")
                )
                if (decision is MdocConsentDecision.Deny) {
                    mutableState.value = MdocHolderSessionState.Terminating(exchange)
                    phase(
                        timeouts.gracefulTermination,
                        ProximityError.Transport("termination_timeout", "Session termination timed out"),
                    ) { sendEmptyResponse(connection, cipher, 0u, budget) }
                    closeReason = ProximityCloseReason.COMPLETED
                    mutableState.value = MdocHolderSessionState.Declined(exchange)
                    return MdocHolderSessionResult.Declined(exchange)
                }
                val resolution = phase(
                    timeouts.keyAuthorization,
                    ProximityError.Policy("response_authorization_timeout", "Response authorization timed out"),
                ) { whileConnected(connection) { requestProcessor.resolve(context, preview) } }
                if (resolution.submissionBindingDigest != preview.submissionBindingDigest) throw ProximityException(
                    ProximityError.Security("changed_submission", "The approved request state changed before submission")
                )
                val terminate = resolution is MdocResponseResolution.TerminateWithoutResponse ||
                    resolution is MdocResponseResolution.Send &&
                    resolution.continuation == MdocSessionContinuation.TERMINATE ||
                    terminateAfterResponse
                if (resolution is MdocResponseResolution.Send) {
                    limits.requireResponse(resolution.exactResponse)
                    requireWithinReaderLimit(request, resolution.exactResponse)
                    mutableState.value = MdocHolderSessionState.SendingResponse(exchange)
                    val encrypted = cipher.encrypt(resolution.exactResponse.copy())
                    send(
                        connection,
                        SessionData(
                            data = encrypted,
                            status = SessionStatusCode.SESSION_TERMINATION.code.takeIf { terminate },
                        ),
                        budget,
                    )
                }
                if (terminate) {
                    mutableState.value = MdocHolderSessionState.Terminating(exchange)
                    if (resolution !is MdocResponseResolution.Send) {
                        phase(
                            timeouts.gracefulTermination,
                            ProximityError.Transport("termination_timeout", "Session termination timed out"),
                        ) { send(connection, SessionData(status = SessionStatusCode.SESSION_TERMINATION.code), budget) }
                    }
                    closeReason = ProximityCloseReason.COMPLETED
                    if (resolution is MdocResponseResolution.TerminateWithoutResponse) {
                        mutableState.value = MdocHolderSessionState.NoData(exchange)
                        return MdocHolderSessionResult.NoData(exchange)
                    }
                    mutableState.value = MdocHolderSessionState.Completed(exchange)
                    return MdocHolderSessionResult.Completed(exchange)
                }
                mutableState.value = MdocHolderSessionState.AwaitingNextRequest(exchange)
                val nextBytes = phase(
                    timeouts.request,
                    ProximityError.Transport("inactivity_timeout", "The reader did not send another request in time"),
                ) { receive(connection, budget) } ?: throw ProximityException(
                    ProximityError.Transport("peer_disconnected", "Reader disconnected before the next request")
                )
                val next = decodeOrReport<SessionData>(connection, nextBytes)
                messageSequencer.validateIncoming(next)
                if (next.data == null) {
                    when (next.statusCode) {
                        SessionStatusCode.SESSION_TERMINATION -> {
                            closeReason = ProximityCloseReason.COMPLETED
                            mutableState.value = MdocHolderSessionState.Completed(exchange)
                            return MdocHolderSessionResult.Completed(exchange)
                        }
                        SessionStatusCode.SESSION_ENCRYPTION_ERROR -> throw ProximityException(
                            ProximityError.Security("reader_session_encryption_error", "Reader reported a session encryption error")
                        )
                        SessionStatusCode.CBOR_DECODING_ERROR -> throw ProximityException(
                            ProximityError.Protocol("reader_cbor_error", "Reader reported a CBOR decoding error")
                        )
                        null -> throw ProximityException(
                            ProximityError.Protocol("missing_session_data", "SessionData did not contain a request")
                        )
                    }
                }
                terminateAfterResponse = next.statusCode == SessionStatusCode.SESSION_TERMINATION
                val encryptedRequest = next.data ?: throw ProximityException(
                    ProximityError.Protocol("missing_session_data", "SessionData did not contain a request")
                )
                incoming = decryptOrReport(connection, cipher, ImmutableBytes.of(encryptedRequest))
            }
        } catch (failure: ProximityException) {
            closeReason = when {
                failure.error.code.endsWith("timeout") -> ProximityCloseReason.TIMEOUT
                failure.error.code == "peer_disconnected" -> ProximityCloseReason.PEER_DISCONNECTED
                else -> closeReason
            }
            throw failure
        } catch (cancelled: CancellationException) {
            closeReason = if (cancelled is TimeoutCancellationException) {
                ProximityCloseReason.TIMEOUT
            } else {
                ProximityCloseReason.CANCELLED
            }
            throw cancelled
        } finally {
            withContext(NonCancellable) {
                cipher?.close()
                val toClose = winningSource?.let(::listOf) ?: prepared?.sources.orEmpty()
                toClose.forEach { source ->
                    try {
                        source.close(closeReason)
                    } catch (_: Exception) {
                        // Continue deterministic cleanup of remaining resources.
                    }
                }
            }
        }
    }

    private suspend fun connectAndReceiveEstablishment(
        prepared: PreparedMdocEngagements,
        budget: MdocSessionBudget,
        onConnected: () -> Unit,
    ): Pair<WinningMdocEngagement, ImmutableBytes> {
        val winner = phase(
            timeouts.transportConnection,
            ProximityError.Transport("connection_timeout", "No reader connected in time"),
        ) { engagementCoordinator.awaitWinner(prepared) }
        onConnected()
        val firstBytes = phase(
            establishmentTimeout(winner.engaged.engagementMode),
            ProximityError.Transport("establishment_timeout", "Session establishment timed out"),
        ) { receive(winner.engaged.connection, budget) } ?: throw ProximityException(
            ProximityError.Transport("peer_disconnected", "Reader disconnected before session establishment")
        )
        return winner to firstBytes
    }

    /** A lost selected connection invalidates application work, including pending holder consent. */
    private suspend fun <T> whileConnected(connection: ProximityConnection, block: suspend () -> T): T = supervisorScope {
        val disconnected = async(start = CoroutineStart.UNDISPATCHED) {
            connection.awaitClosed()
            throw ProximityException(
                ProximityError.Transport("peer_disconnected", "The connection to the reader was lost"),
            )
        }
        // Register closure first so an already closed connection cannot start a new review or signing operation.
        val operation = async(start = CoroutineStart.LAZY) { block() }
        try {
            select {
                disconnected.onAwait { it }
                operation.onAwait { it }
            }
        } finally {
            disconnected.cancel()
            operation.cancel()
        }
    }

    private suspend fun receive(connection: ProximityConnection, budget: MdocSessionBudget): ImmutableBytes? =
        connection.receive()?.also {
            requireWithinTransportLimit(it)
            limits.requireSessionMessage(it)
            budget.account(it)
        }

    private suspend inline fun <reified T> decodeOrReport(
        connection: ProximityConnection,
        bytes: ImmutableBytes,
    ): T {
        val encoded = bytes.copy()
        return try {
            MdocCborGuard.validate(encoded, limits.maximumCborDepth, limits.maximumCborItems)
            coseCompliantCbor.decodeFromByteArray<T>(encoded)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            trySendStatus(connection, SessionStatusCode.CBOR_DECODING_ERROR)
            throw ProximityException(ProximityError.Protocol("invalid_cbor", "Invalid ${T::class.simpleName}"), failure)
        }
    }

    private suspend fun decodeEstablishmentOrReport(
        connection: ProximityConnection,
        bytes: ImmutableBytes,
    ): SessionEstablishment {
        // Classify errors inside a correctly wrapped reader key separately from envelope CBOR errors.
        val envelope = decodeOrReport<CborElement>(connection, bytes) as? CborMap
        val keyBytes = envelope?.get(CborString("eReaderKey")) as? CborByteString
        if (keyBytes != null && 24uL in keyBytes.tags) {
            try {
                val encoded = keyBytes.toByteArray()
                MdocCborGuard.validate(encoded, limits.maximumCborDepth, limits.maximumCborItems)
                require(coseCompliantCbor.decodeFromByteArray<CoseKey>(encoded).d == null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                trySendStatus(connection, SessionStatusCode.SESSION_ENCRYPTION_ERROR)
                throw ProximityException(ProximityError.Security("invalid_reader_key", "Reader session key is invalid"), failure)
            }
        }
        return decodeOrReport(connection, bytes)
    }

    private suspend fun decodeRequestOrReport(
        connection: ProximityConnection,
        cipher: MdocSessionCipher,
        bytes: ImmutableBytes,
        budget: MdocSessionBudget,
    ): DeviceRequest = try {
        val encoded = bytes.copy()
        MdocCborGuard.validate(encoded, limits.maximumCborDepth, limits.maximumCborItems, includeEmbeddedCbor = true)
        coseCompliantCbor.decodeFromByteArray<DeviceRequest>(encoded).also {
            validateRequestLimits(it)
            validateAdvertisedFeatures(it)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        try {
            phase(timeouts.gracefulTermination, ProximityError.Transport("termination_timeout", "Session termination timed out")) {
                sendEmptyResponse(connection, cipher, 10u, budget)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Preserve the original request failure if the connection cannot carry the error response.
        }
        throw failure as? ProximityException
            ?: ProximityException(ProximityError.Protocol("invalid_cbor", "Invalid DeviceRequest"), failure)
    }

    private suspend fun sendEmptyResponse(
        connection: ProximityConnection,
        cipher: MdocSessionCipher,
        status: UInt,
        budget: MdocSessionBudget,
    ) {
        val response = ImmutableBytes.of(coseCompliantCbor.encodeToByteArray(
            DeviceResponse.serializer(), DeviceResponse(version = "1.0", status = status),
        ))
        limits.requireResponse(response)
        send(connection, SessionData(data = cipher.encrypt(response.copy()), status = SessionStatusCode.SESSION_TERMINATION.code), budget)
    }

    private suspend fun decryptOrReport(
        connection: ProximityConnection,
        cipher: MdocSessionCipher,
        data: ImmutableBytes,
    ): ImmutableBytes = try {
        ImmutableBytes.of(cipher.decrypt(data.copy()))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        trySendStatus(connection, SessionStatusCode.SESSION_ENCRYPTION_ERROR)
        throw ProximityException(
            ProximityError.Security("session_authentication_failed", "Session message authentication failed"),
            failure,
        )
    }

    private suspend fun send(connection: ProximityConnection, message: SessionData, budget: MdocSessionBudget) {
        val sequenced = messageSequencer.sequence(message)
        val encoded = ImmutableBytes.of(coseCompliantCbor.encodeToByteArray(SessionData.serializer(), sequenced))
        requireWithinTransportLimit(encoded)
        limits.requireSessionMessage(encoded)
        budget.account(encoded)
        connection.send(encoded)
    }

    private suspend fun trySendStatus(connection: ProximityConnection, status: SessionStatusCode) {
        try {
            val message = messageSequencer.sequence(SessionData(status = status.code))
            val encoded = ImmutableBytes.of(
                coseCompliantCbor.encodeToByteArray(SessionData.serializer(), message)
            )
            requireWithinTransportLimit(encoded)
            limits.requireSessionMessage(encoded)
            connection.send(encoded)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The original protocol/security failure remains authoritative.
        }
    }

    private fun validateRequestLimits(request: DeviceRequest) {
        if (request.docRequests.size > limits.maximumDocuments) throw limit("document_count", "Too many document requests")
        request.docRequests.forEach { docRequest ->
            val namespaces = docRequest.itemsRequest.value.namespaces
            if (namespaces.size > limits.maximumNamespacesPerDocument) throw limit("namespace_count", "Too many namespaces")
            if (namespaces.values.any { it.entries.size > limits.maximumElementsPerNamespace }) {
                throw limit("element_count", "Too many data elements in a namespace")
            }
        }
    }

    private fun validateAdvertisedFeatures(request: DeviceRequest) {
        if (request.readerAuthAll != null && !capabilities.selected(MdocProtocolFeature.READER_AUTH_ALL)) throw ProximityException(
            ProximityError.Protocol("reader_auth_all_not_advertised", "ReaderAuthAll was not advertised for this session")
        )
        val extended = request.deviceRequestInfo != null || request.docRequests.any {
            it.itemsRequest.value.requestInfo != null
        }
        if (extended && !capabilities.selected(MdocProtocolFeature.EXTENDED_REQUESTS)) throw ProximityException(
            ProximityError.Protocol("extended_request_not_advertised", "Extended request processing was not advertised")
        )
    }

    private fun requireWithinReaderLimit(request: DeviceRequest, response: ImmutableBytes) {
        val readerLimit = request.docRequests.mapNotNull { it.itemsRequest.value.requestInfo?.maximumResponseSize }
            .minOrNull()?.toLong() ?: return
        if (response.size.toLong() > readerLimit) throw ProximityException(
            ProximityError.Policy("reader_response_limit", "The response exceeds the reader's declared maximum size")
        )
    }

    private fun requireWithinTransportLimit(message: ImmutableBytes) {
        if (message.size > engagementContext.maximumMessageBytes) throw ProximityException(
            ProximityError.Transport(
                "transport_message_limit",
                "The session message exceeds the selected transport profile limit",
            )
        )
    }

    private fun limit(code: String, message: String) = ProximityException(ProximityError.Protocol(code, message))

    private fun establishmentTimeout(mode: MdocEngagementMode): Duration = when (mode) {
        is MdocEngagementMode.Qr -> timeouts.qrSessionEstablishment
        is MdocEngagementMode.Nfc -> timeouts.nfcSessionEstablishment
    }

    private fun consentBinding(
        request: ImmutableBytes,
        transcript: ImmutableBytes,
        exchange: Int,
        preview: MdocRequestPreview,
    ): ImmutableBytes {
        val exchangeBytes = byteArrayOf(
            (exchange ushr 24).toByte(),
            (exchange ushr 16).toByte(),
            (exchange ushr 8).toByte(),
            exchange.toByte(),
        )
        return ImmutableBytes.of(
            SHA256().digest(
                "walt.id/mdoc-consent/v2".encodeToByteArray() +
                    bindingLengthPrefixed(request.copy()) +
                    bindingLengthPrefixed(transcript.copy()) +
                    exchangeBytes +
                    preview.submissionBindingDigest.copy() +
                    applicationAuthorizationBindings(preview.applicationAuthorizations)
            )
        )
    }

    private fun applicationAuthorizationBindings(
        authorizations: List<MdocApplicationAuthorization>,
    ): ByteArray = authorizations.fold(bindingIntBytes(authorizations.size)) { bytes, authorization ->
        bytes + authorization.consentBindingDigest().copy()
    }

    private suspend fun <T> phase(duration: Duration, error: ProximityError, block: suspend () -> T): T {
        val completed = withTimeoutOrNull(duration) { CompletedPhase(block()) }
            ?: throw ProximityException(error)
        return completed.value
    }

    /** Distinguishes a completed nullable result from this phase's own timeout. */
    private class CompletedPhase<T>(val value: T)

    private class MdocSessionBudget(private val maximumBytes: Long) {
        private var bytes = 0L
        fun account(message: ImmutableBytes) {
            bytes += message.size
            if (bytes > maximumBytes) throw ProximityException(
                ProximityError.Protocol("session_byte_limit", "The session exceeded the cumulative byte limit")
            )
        }
    }
}
