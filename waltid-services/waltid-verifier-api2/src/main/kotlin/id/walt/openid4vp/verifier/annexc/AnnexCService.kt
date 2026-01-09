@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package id.walt.openid4vp.verifier.annexc

import id.walt.iso18013.annexc.AnnexC
import id.walt.iso18013.annexc.AnnexCHpkeKeyGeneratorJvm
import id.walt.iso18013.annexc.AnnexCRequestBuilder
import id.walt.iso18013.annexc.AnnexCResponseVerifierJvm
import id.walt.iso18013.annexc.AnnexCTranscriptBuilder
import id.walt.iso18013.annexc.cbor.Base64UrlNoPad
import id.walt.openid4vp.verifier.annexc.openapi.AnnexCOpenApi
import id.walt.mdoc.parser.MdocParser
import id.walt.mdoc.verification.MdocVerifier
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.klogging.logger
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

private val log = logger("AnnexCService")

object AnnexCService {
    private const val ANNEX_C = "annex-c"
    private const val CLEANUP_INTERVAL_SECONDS = 60L
    private val terminalRetention = 5.minutes
    private val includeDeviceResponseCbor =
        System.getenv("ANNEXC_INCLUDE_DEVICE_RESPONSE")?.toBoolean() ?: false

    private val random = SecureRandom()
    private val processingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val sessions = ConcurrentHashMap<String, AnnexCSession>()
    private val sessionLocks = ConcurrentHashMap<String, Mutex>()

    init {
        cleanupScope.launch {
            while (true) {
                evictExpiredSessions()
                delay(CLEANUP_INTERVAL_SECONDS.seconds)
            }
        }
    }

    @Serializable
    data class AnnexCCreateRequest(
        val docType: String,
        val requestedElements: Map<String, List<String>>,
        val policies: List<String> = emptyList(),
        val origin: String,
        val ttlSeconds: Long? = null,
    )

    @Serializable
    data class AnnexCCreateResponse(
        val sessionId: String,
        val expiresAt: String,
    )

    @Serializable
    data class AnnexCRequestRequest(
        val sessionId: String,
        val intentToRetain: Boolean = false,
    )

    @Serializable
    data class AnnexCRequestResponse(
        val protocol: String,
        val data: Data,
        //val meta: Meta,
    ) {
        @Serializable
        data class Data(
            @SerialName("deviceRequest")
            val deviceRequest: String,
            @SerialName("encryptionInfo")
            val encryptionInfo: String,
        )

        @Serializable
        data class Meta(
            val sessionId: String,
            val expiresAt: String,
        )
    }

    @Serializable
    data class AnnexCResponseRequest(
        val sessionId: String,
        val response: String,
    )

    @Serializable
    data class AnnexCResponseAck(
        val status: String = "received",
    )

    @Serializable
    data class AnnexCInfoResponse(
        val sessionId: String,
        val status: AnnexCSessionStatus,
        val origin: String,
        val expiresAt: String,
        val docType: String,
        val requestedElements: Map<String, List<String>>,
        val policies: List<String> = emptyList(),

        val deviceRequest: String? = null,
        val encryptionInfo: String? = null,
        val encryptedResponse: String? = null,

        val deviceResponseCbor: String? = null,
        val mdocVerificationResult: id.walt.mdoc.verification.VerificationResult? = null,
        val error: String? = null,
    )

    @Serializable
    enum class AnnexCSessionStatus {
        created,
        request_built,
        response_received,
        processing,
        decrypted,
        verified,
        failed,
        expired,
    }

    data class AnnexCSession(
        val id: String,
        val createdAt: Instant,
        val expiresAt: Instant,
        val origin: String,
        val docType: String,
        val requestedElements: Map<String, List<String>>,
        val policies: List<String>,
        val nonce: ByteArray,
        val recipientPrivateKey: ByteArray,
        val recipientPublicKey: id.walt.cose.CoseKey,
        var status: AnnexCSessionStatus = AnnexCSessionStatus.created,
        var deviceRequestB64: String? = null,
        var encryptionInfoB64: String? = null,
        var encryptedResponseB64: String? = null,
        var deviceResponseCborBytes: ByteArray? = null,
        var verificationResult: id.walt.mdoc.verification.VerificationResult? = null,
        var error: String? = null,
        var completedAt: Instant? = null,
    )

    private data class AnnexCProcessingContext(
        val sessionId: String,
        val origin: String,
        val encryptionInfoB64: String,
        val recipientPrivateKey: ByteArray,
        val responseB64: String,
    )

    fun Route.registerRoute() {
        route(ANNEX_C) {
            route("", { tags("Annex C (ISO 18013-7)") }) {
                post<AnnexCCreateRequest>("create", AnnexCOpenApi.createDocs) { request ->
                    require(request.ttlSeconds == null || request.ttlSeconds > 0) {
                        "ttlSeconds must be > 0"
                    }
                    val ttl = (request.ttlSeconds?.seconds ?: 5.minutes)
                    val now = Clock.System.now()
                    val expiresAt = now + ttl

                    val nonce = ByteArray(16).also { random.nextBytes(it) }
                    val keyPair = AnnexCHpkeKeyGeneratorJvm.generateRecipientKeyPair()

                    val session = AnnexCSession(
                        id = Uuid.random().toString(),
                        createdAt = now,
                        expiresAt = expiresAt,
                        origin = request.origin,
                        docType = request.docType,
                        requestedElements = request.requestedElements,
                        policies = request.policies,
                        nonce = nonce,
                        recipientPrivateKey = keyPair.recipientPrivateKey,
                        recipientPublicKey = keyPair.recipientPublicKey,
                    )
                    sessions[session.id] = session
                    sessionLocks[session.id] = Mutex()

                    call.respond(AnnexCCreateResponse(sessionId = session.id, expiresAt = session.expiresAt.toString()))
                }

                post<AnnexCRequestRequest>("request", AnnexCOpenApi.requestDocs) { request ->
                    val session = sessions[request.sessionId]
                        ?: return@post call.respond(HttpStatusCode.NotFound, "Unknown Annex C session id")
                    val lock = getSessionLock(request.sessionId)

                    val annexRequest = lock.withLock {
                        if (isExpired(session)) {
                            session.status = AnnexCSessionStatus.expired
                            markCompleted(session)
                            throw IllegalArgumentException("Session expired")
                        }

                        AnnexCRequestBuilder.build(
                            docType = session.docType,
                            requestedElements = session.requestedElements,
                            nonce = session.nonce,
                            recipientPublicKey = session.recipientPublicKey,
                            intentToRetain = request.intentToRetain,
                        ).also {
                            session.deviceRequestB64 = it.deviceRequestB64
                            session.encryptionInfoB64 = it.encryptionInfoB64
                            session.status = AnnexCSessionStatus.request_built
                        }
                    }

                    call.respond(
                        AnnexCRequestResponse(
                            protocol = AnnexC.PROTOCOL,
                            data = AnnexCRequestResponse.Data(
                                deviceRequest = annexRequest.deviceRequestB64,
                                encryptionInfo = annexRequest.encryptionInfoB64
                            )
//                            ,meta = AnnexCRequestResponse.Meta(
//                                sessionId = session.id,
//                                expiresAt = session.expiresAt.toString()
//                            )
                        )
                    )
                }

                post<AnnexCResponseRequest>("response", AnnexCOpenApi.responseDocs) { request ->
                    val session = sessions[request.sessionId]
                        ?: return@post call.respond(HttpStatusCode.NotFound, "Unknown Annex C session id")
                    val lock = getSessionLock(request.sessionId)

                    val context = lock.withLock {
                        if (isExpired(session)) {
                            session.status = AnnexCSessionStatus.expired
                            markCompleted(session)
                            throw IllegalArgumentException("Session expired")
                        }
                        val encryptionInfoB64 = requireNotNull(session.encryptionInfoB64) {
                            "Missing encryptionInfo (call /annex-c/request first)"
                        }
                        session.encryptedResponseB64 = request.response
                        session.status = AnnexCSessionStatus.response_received
                        session.error = null
                        AnnexCProcessingContext(
                            sessionId = session.id,
                            origin = session.origin,
                            encryptionInfoB64 = encryptionInfoB64,
                            recipientPrivateKey = session.recipientPrivateKey.copyOf(),
                            responseB64 = request.response
                        )
                    }

                    processingScope.launch {
                        processResponse(context)
                    }

                    call.respond(AnnexCResponseAck())
                }

                get("info", AnnexCOpenApi.infoDocs) {
                    val sessionId = call.request.queryParameters["sessionId"]
                        ?: throw IllegalArgumentException("Missing query parameter: sessionId")
                    val session = sessions[sessionId]
                        ?: return@get call.respond(HttpStatusCode.NotFound, "Unknown Annex C session id")
                    val lock = getSessionLock(sessionId)

                    val response = lock.withLock {
                        if (isExpired(session) && session.status != AnnexCSessionStatus.verified && session.status != AnnexCSessionStatus.failed) {
                            session.status = AnnexCSessionStatus.expired
                            markCompleted(session)
                        }

                        val deviceResponseB64 =
                            if (includeDeviceResponseCbor) session.deviceResponseCborBytes?.let { Base64UrlNoPad.encode(it) } else null
                        AnnexCInfoResponse(
                            sessionId = session.id,
                            status = session.status,
                            origin = session.origin,
                            expiresAt = session.expiresAt.toString(),
                            docType = session.docType,
                            requestedElements = session.requestedElements,
                            policies = session.policies,
                            deviceRequest = session.deviceRequestB64,
                            encryptionInfo = session.encryptionInfoB64,
                            encryptedResponse = session.encryptedResponseB64,
                            deviceResponseCbor = deviceResponseB64,
                            mdocVerificationResult = session.verificationResult,
                            error = session.error
                        )
                    }

                    call.respond(
                        response
                    )
                }
            }
        }
    }

    private fun isExpired(session: AnnexCSession): Boolean = Clock.System.now() > session.expiresAt

    private fun getSessionLock(sessionId: String): Mutex =
        sessionLocks.computeIfAbsent(sessionId) { Mutex() }

    private suspend fun processResponse(context: AnnexCProcessingContext) {
        val session = sessions[context.sessionId] ?: return
        val lock = getSessionLock(context.sessionId)

        try {
            lock.withLock {
                if (isExpired(session)) {
                    session.status = AnnexCSessionStatus.expired
                    markCompleted(session)
                    return
                }
                session.status = AnnexCSessionStatus.processing
            }

            val deviceResponseBytes = AnnexCResponseVerifierJvm.decryptToDeviceResponse(
                encryptedResponseB64 = context.responseB64,
                encryptionInfoB64 = context.encryptionInfoB64,
                origin = context.origin,
                recipientPrivateKey = context.recipientPrivateKey
            )

            lock.withLock {
                session.deviceResponseCborBytes = deviceResponseBytes
                session.status = AnnexCSessionStatus.decrypted
            }

            val sessionTranscript = AnnexCTranscriptBuilder.buildSessionTranscript(
                encryptionInfoB64 = context.encryptionInfoB64,
                origin = context.origin
            )

            val deviceResponseB64 = Base64UrlNoPad.encode(deviceResponseBytes)
            val document = MdocParser.parseToDocument(deviceResponseB64)
            val verificationResult = MdocVerifier.verify(document, sessionTranscript)

            lock.withLock {
                session.verificationResult = verificationResult
                session.status = if (verificationResult.valid) {
                    AnnexCSessionStatus.verified
                } else {
                    AnnexCSessionStatus.failed
                }
                markCompleted(session)
                wipeSensitive(session)
            }
        } catch (e: Exception) {
            lock.withLock {
                session.error = e.message ?: e.toString()
                session.status = AnnexCSessionStatus.failed
                markCompleted(session)
                wipeSensitive(session)
            }
            log.warn(e) { "Annex C processing failed for session ${context.sessionId}" }
        }
    }

    private fun markCompleted(session: AnnexCSession) {
        if (session.completedAt == null) {
            session.completedAt = Clock.System.now()
        }
    }

    private fun wipeSensitive(session: AnnexCSession) {
        session.recipientPrivateKey.fill(0)
        if (!includeDeviceResponseCbor) {
            session.deviceResponseCborBytes?.fill(0)
            session.deviceResponseCborBytes = null
        }
    }

    private fun evictExpiredSessions() {
        val now = Clock.System.now()
        sessions.entries.removeIf { (sessionId, session) ->
            val lock = sessionLocks[sessionId] ?: return@removeIf false
            if (!lock.tryLock()) return@removeIf false
            try {
                val terminalExpired = session.completedAt?.let { now > it + terminalRetention } == true
                val expired = now > session.expiresAt
                if (expired || terminalExpired) {
                    wipeSensitive(session)
                    sessionLocks.remove(sessionId)
                    true
                } else {
                    false
                }
            } finally {
                lock.unlock()
            }
        }
    }
}
