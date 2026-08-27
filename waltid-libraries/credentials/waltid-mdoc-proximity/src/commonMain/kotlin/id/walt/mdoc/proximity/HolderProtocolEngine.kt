@file:OptIn(
    kotlinx.serialization.ExperimentalSerializationApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
)

package id.walt.mdoc.proximity

import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.mdoc.crypto.MdocCryptoHelper
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.encoding.ExactCbor
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.engagement.DeviceEngagement
import id.walt.mdoc.objects.engagement.DeviceEngagementCapabilities
import id.walt.mdoc.objects.engagement.DeviceEngagementSecurity
import id.walt.mdoc.objects.engagement.DeviceRetrievalMethod
import id.walt.mdoc.objects.session.SessionData
import id.walt.mdoc.objects.session.SessionEstablishment
import id.walt.mdoc.objects.session.SessionStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.cbor.CborElement
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.io.encoding.Base64
import kotlin.time.Duration

fun interface SessionTranscriptFactory {
    fun create(
        deviceEngagementBytes: ImmutableBytes,
        eReaderKeyBytes: ImmutableBytes,
        connectionMethod: DeviceRetrievalMethod,
    ): SessionTranscript
}

object QrSessionTranscriptFactory : SessionTranscriptFactory {
    override fun create(
        deviceEngagementBytes: ImmutableBytes,
        eReaderKeyBytes: ImmutableBytes,
        connectionMethod: DeviceRetrievalMethod,
    ): SessionTranscript = SessionTranscript.forQr(deviceEngagementBytes.copy(), eReaderKeyBytes.copy())
}

data class MdocEngagement(
    val engagement: ExactCbor<DeviceEngagement>,
    val qrPayload: String?,
)

class MdocDeviceEngagementFactory {
    suspend fun create(
        eDeviceKey: Key,
        methods: List<DeviceRetrievalMethod>,
        context: EngagementContext,
        capabilities: MdocSessionCapabilities,
    ): MdocEngagement {
        require(methods.isNotEmpty()) { "At least one retrieval method is required" }
        require(capabilities.profile == context.profile) { "Capability profile must match the engagement profile" }
        MdocSessionKeyValidator.requireSupportedLocalKey(eDeviceKey)
        require(eDeviceKey.spec.toMdocSessionCurve() == capabilities.selectedCurve) {
            "Selected session curve does not match the ephemeral device key"
        }
        val publicJwk = eDeviceKey.capabilities.publicKeyExporter?.exportPublicKey() as? EncodedKey.Jwk
            ?: throw IllegalArgumentException("Ephemeral device key cannot export a public JWK")
        val publicCose = publicJwk.toCoseKey()
        val encodedCose = coseCompliantCbor.encodeToByteArray(CoseKey.serializer(), publicCose)
        val engagementCapabilities = capabilities.toDeviceEngagementCapabilities()
        val usesEdition2Fields = engagementCapabilities != null
        val engagement = DeviceEngagement(
            version = if (usesEdition2Fields) DeviceEngagement.VERSION_1_1 else DeviceEngagement.VERSION_1_0,
            security = DeviceEngagementSecurity(1u, ByteStringWrapper(publicCose, encodedCose)),
            deviceRetrievalMethods = methods.toList().takeIf { context.engagementMode is MdocEngagementMode.Qr },
            originInfos = emptyList<CborElement>().takeIf { usesEdition2Fields },
            capabilities = engagementCapabilities,
        )
        val exact = ExactCbor.of(
            engagement,
            coseCompliantCbor.encodeToByteArray(DeviceEngagement.serializer(), engagement),
        )
        val qrPayload = if (context.engagementMode is MdocEngagementMode.Qr) {
            "mdoc:" + Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(exact.encodedCopy())
        } else null
        return MdocEngagement(exact, qrPayload)
    }
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
    val credentialIds: List<String> = credentialIds.toList()
    val elements: List<PreviewElement> = elements.toList()

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
    val documents: List<PreviewDocument> = documents.toList()
    val purposeHints: Map<String, Int> = purposeHints.toMap()
    /** Wallet-profile results already validated and normalized for display during holder consent. */
    val applicationAuthorizations: List<MdocApplicationAuthorization> = applicationAuthorizations.toList()

    init {
        require(this.documents.isNotEmpty()) { "A request preview must contain at least one document" }
        require(submissionBindingDigest.size == SHA256_BYTES) { "Submission binding must be a SHA-256 digest" }
    }

    private companion object { const val SHA256_BYTES = 32 }
}

data class MdocHolderRequestContext(
    val request: ExactCbor<DeviceRequest>,
    val transcript: ExactCbor<SessionTranscript>,
    val exchange: Int,
) {
    init {
        require(exchange > 0)
    }
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

interface MdocHolderRequestProcessor {
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
        val availableTransports: Set<ProximityTransportKind>,
        val unavailableTransports: Map<ProximityTransportKind, ProximityError>,
    ) : MdocHolderSessionState
    data class Connecting(
        val qrPayload: String?,
        val availableTransports: Set<ProximityTransportKind>,
        val unavailableTransports: Map<ProximityTransportKind, ProximityError>,
    ) : MdocHolderSessionState
    data class AwaitingRequest(val exchange: Int) : MdocHolderSessionState {
        init { require(exchange > 0) }
    }
    data class ReviewRequired(val prompt: MdocConsentPrompt) : MdocHolderSessionState
    data class SendingResponse(val exchange: Int) : MdocHolderSessionState {
        init { require(exchange > 0) }
    }
    data class Declined(val exchange: Int) : MdocHolderSessionState {
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
        is MdocHolderSessionState.Failed,
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
        is MdocHolderSessionState.SendingResponse -> setOf(MdocHolderAction.CANCEL)
    }

sealed interface MdocHolderSessionResult {
    data class Declined(val exchange: Int) : MdocHolderSessionResult
    data class Completed(val exchanges: Int) : MdocHolderSessionResult
    data class Failed(val error: ProximityError) : MdocHolderSessionResult
}

/**
 * Radio-independent holder state machine. Platform adapters only prepare transports and move complete messages.
 */
class MdocHolderProtocolEngine(
    private val eDeviceKey: Key,
    private val transportProviders: List<ProximityTransportProvider>,
    private val requestProcessor: MdocHolderRequestProcessor,
    private val consentHandler: MdocConsentHandler,
    private val engagementContext: EngagementContext,
    private val capabilities: MdocSessionCapabilities,
    private val limits: MdocProximityLimits = MdocProximityLimits(),
    private val timeouts: MdocProximityTimeouts = MdocProximityTimeouts(),
    private val transportCoordinator: TransportCoordinator = TransportCoordinator(),
    private val engagementFactory: MdocDeviceEngagementFactory = MdocDeviceEngagementFactory(),
) {
    private val mutableState = MutableStateFlow<MdocHolderSessionState>(MdocHolderSessionState.Idle)
    val state: StateFlow<MdocHolderSessionState> = mutableState.asStateFlow()
    private val startMutex = Mutex()
    private var started = false

    init {
        require(capabilities.profile == engagementContext.profile) {
            "Capability profile must match the engagement profile"
        }
        val negotiatedEstablishment = (engagementContext.engagementMode as? MdocEngagementMode.Nfc)
            ?.negotiatedSessionEstablishment
        require(
            capabilities.selected(MdocProtocolFeature.NEGOTIATED_HANDOVER_SESSION_ESTABLISHMENT) ==
                (negotiatedEstablishment != null)
        ) {
            "Negotiated handover capability requires exact handover SessionEstablishment bytes"
        }
        negotiatedEstablishment?.let(limits::requireEngagementOrHandover)
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
        val negotiatedEstablishment = (engagementContext.engagementMode as? MdocEngagementMode.Nfc)
            ?.negotiatedSessionEstablishment
        mutableState.value = MdocHolderSessionState.Preparing(engagementContext.profile.id)
        var prepared: PreparedTransports? = null
        var winningPrepared: PreparedTransport? = null
        var cipher: MdocSessionCipher? = null
        var closeReason = ProximityCloseReason.PROTOCOL_ERROR
        val budget = MdocSessionBudget(limits.maximumCumulativeSessionBytes)
        try {
            prepared = transportCoordinator.prepare(transportProviders, engagementContext, scope)
            val engagement = engagementFactory.create(
                eDeviceKey,
                prepared.connectionMethods,
                engagementContext,
                capabilities,
            )
            val engagementBytes = ImmutableBytes.of(engagement.engagement.encodedCopy())
            limits.requireEngagementOrHandover(engagementBytes)
            val kinds = prepared.transports.map { it.kind }.toSet()
            mutableState.value = MdocHolderSessionState.EngagementReady(
                engagement.qrPayload,
                kinds,
                prepared.unavailable,
            )
            mutableState.value = MdocHolderSessionState.Connecting(
                engagement.qrPayload,
                kinds,
                prepared.unavailable,
            )

            val (winner, firstBytes) = if (engagementContext.engagementMode is MdocEngagementMode.Qr) {
                phase(
                    timeouts.qrEngagementLifetime,
                    ProximityError.Protocol("engagement_timeout", "The QR engagement expired before session establishment"),
                ) { connectAndReceiveEstablishment(prepared, budget) }
            } else {
                connectAndReceiveEstablishment(prepared, budget)
            }
            winningPrepared = winner.prepared
            val connection = winner.connection
            if (negotiatedEstablishment != null && firstBytes != negotiatedEstablishment) {
                throw ProximityException(
                    ProximityError.Security(
                        "negotiated_establishment_mismatch",
                        "SessionEstablishment differs from the negotiated handover copy",
                    )
                )
            }
            val establishment = decodeOrReport<SessionEstablishment>(connection, firstBytes)
            val transcript = winner.prepared.sessionTranscriptFactory.create(
                engagementBytes,
                ImmutableBytes.of(establishment.eReaderKey.serialized),
                winner.prepared.connectionMethod,
            )
            val transcriptBytes = MdocCryptoHelper.buildSessionTranscriptBytes(transcript)
            val exactTranscript = ImmutableBytes.of(transcriptBytes)
            limits.requireEngagementOrHandover(exactTranscript)
            MdocCborGuard.validate(transcriptBytes, limits.maximumCborDepth, limits.maximumCborItems)
            cipher = MdocSessionCipher.establishForHolder(
                eDeviceKey,
                establishment.eReaderKey.value,
                transcriptBytes,
            )

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
                val request = decodeOrReport<DeviceRequest>(connection, incoming)
                validateRequestLimits(request)
                validateAdvertisedFeatures(request)
                val context = MdocHolderRequestContext(
                    request = ExactCbor.of(request, incoming.copy()),
                    transcript = ExactCbor.of(transcript, exactTranscript.copy()),
                    exchange = exchange,
                )
                val preview = phase(
                    timeouts.request,
                    ProximityError.Protocol("request_processing_timeout", "Request preview processing timed out"),
                ) { requestProcessor.preview(context) }
                val token = consentBinding(incoming, exactTranscript, exchange, preview)
                val prompt = MdocConsentPrompt(token, exchange, preview)
                mutableState.value = MdocHolderSessionState.ReviewRequired(prompt)
                val decision = phase(
                    timeouts.consent,
                    ProximityError.Policy("consent_timeout", "Holder consent timed out"),
                ) { consentHandler.decide(prompt) }
                if (decision.bindingToken != token) throw ProximityException(
                    ProximityError.Security("stale_consent", "Consent does not belong to the active request preview")
                )
                if (decision is MdocConsentDecision.Deny) {
                    phase(
                        timeouts.gracefulTermination,
                        ProximityError.Transport("termination_timeout", "Session termination timed out"),
                    ) { send(connection, SessionData(status = SessionStatusCode.SESSION_TERMINATION.code), budget) }
                    closeReason = ProximityCloseReason.COMPLETED
                    mutableState.value = MdocHolderSessionState.Declined(exchange)
                    return MdocHolderSessionResult.Declined(exchange)
                }
                val resolution = phase(
                    timeouts.keyAuthorization,
                    ProximityError.Policy("response_authorization_timeout", "Response authorization timed out"),
                ) { requestProcessor.resolve(context, preview) }
                if (resolution.submissionBindingDigest != preview.submissionBindingDigest) throw ProximityException(
                    ProximityError.Security("changed_submission", "The approved request state changed before submission")
                )
                if (resolution is MdocResponseResolution.Send) {
                    limits.requireResponse(resolution.exactResponse)
                    requireWithinReaderLimit(request, resolution.exactResponse)
                    mutableState.value = MdocHolderSessionState.SendingResponse(exchange)
                    val encrypted = cipher.encrypt(resolution.exactResponse.copy())
                    send(connection, SessionData(data = encrypted), budget)
                }
                val terminate = resolution is MdocResponseResolution.TerminateWithoutResponse ||
                    resolution is MdocResponseResolution.Send &&
                    resolution.continuation == MdocSessionContinuation.TERMINATE
                if (terminate || terminateAfterResponse) {
                    phase(
                        timeouts.gracefulTermination,
                        ProximityError.Transport("termination_timeout", "Session termination timed out"),
                    ) { send(connection, SessionData(status = SessionStatusCode.SESSION_TERMINATION.code), budget) }
                    closeReason = ProximityCloseReason.COMPLETED
                    mutableState.value = MdocHolderSessionState.Completed(exchange)
                    return MdocHolderSessionResult.Completed(exchange)
                }

                val nextBytes = phase(
                    timeouts.request,
                    ProximityError.Transport("inactivity_timeout", "The reader did not send another request in time"),
                ) { receive(connection, budget) } ?: throw ProximityException(
                    ProximityError.Transport("peer_disconnected", "Reader disconnected before the next request")
                )
                val next = decodeOrReport<SessionData>(connection, nextBytes)
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
            if (failure.error.code.endsWith("timeout")) closeReason = ProximityCloseReason.TIMEOUT
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
                val toClose = winningPrepared?.let(::listOf) ?: prepared?.transports.orEmpty()
                toClose.forEach { transport ->
                    try {
                        transport.close(closeReason)
                    } catch (_: Exception) {
                        // Continue deterministic cleanup of remaining resources.
                    }
                }
            }
        }
    }

    private suspend fun connectAndReceiveEstablishment(
        prepared: PreparedTransports,
        budget: MdocSessionBudget,
    ): Pair<WinningConnection, ImmutableBytes> {
        val winner = phase(
            timeouts.transportConnection,
            ProximityError.Transport("connection_timeout", "No reader connected in time"),
        ) { transportCoordinator.awaitWinner(prepared) }
        val firstBytes = phase(
            establishmentTimeout(),
            ProximityError.Transport("establishment_timeout", "Session establishment timed out"),
        ) { receive(winner.connection, budget) } ?: throw ProximityException(
            ProximityError.Transport("peer_disconnected", "Reader disconnected before session establishment")
        )
        return winner to firstBytes
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
        val encoded = ImmutableBytes.of(coseCompliantCbor.encodeToByteArray(SessionData.serializer(), message))
        requireWithinTransportLimit(encoded)
        limits.requireSessionMessage(encoded)
        budget.account(encoded)
        connection.send(encoded)
    }

    private suspend fun trySendStatus(connection: ProximityConnection, status: SessionStatusCode) {
        try {
            val encoded = ImmutableBytes.of(
                coseCompliantCbor.encodeToByteArray(SessionData.serializer(), SessionData(status = status.code))
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
        val responseLimits = request.docRequests.mapNotNull {
            it.itemsRequest.value.requestInfo?.maximumResponseSize
        }.distinct()
        if (responseLimits.size > 1) throw limit(
            "ambiguous_response_limit",
            "The request declares conflicting maximum response sizes",
        )
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

    private fun establishmentTimeout(): Duration = when (engagementContext.engagementMode) {
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
