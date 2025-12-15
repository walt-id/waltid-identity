@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package id.walt.openid4vp.verifier.annexc

import id.walt.iso18013.annexc.AnnexC
import id.walt.iso18013.annexc.AnnexCHpkeKeyGeneratorJvm
import id.walt.iso18013.annexc.AnnexCRequestBuilder
import id.walt.iso18013.annexc.AnnexCResponseVerifierJvm
import id.walt.iso18013.annexc.AnnexCTranscriptBuilder
import id.walt.iso18013.annexc.cbor.Base64UrlNoPad
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
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val log = logger("AnnexCService")

object AnnexCService {
    private const val ANNEX_C = "annex-c"

    private val random = SecureRandom()
    private val processingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val sessions = ConcurrentHashMap<String, AnnexCSession>()

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
        val meta: Meta,
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
    )

    fun Route.registerRoute() {
        route(ANNEX_C) {
            route("", { tags("Annex C (ISO 18013-7)") }) {
                post<AnnexCCreateRequest>("create", {
                    summary = "Create Annex C session"
                    response { HttpStatusCode.OK to { body<AnnexCCreateResponse>() } }
                }) { request ->
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

                    call.respond(AnnexCCreateResponse(sessionId = session.id, expiresAt = session.expiresAt.toString()))
                }

                post<AnnexCRequestRequest>("request", {
                    summary = "Build DeviceRequest + EncryptionInfo for DC API"
                    response { HttpStatusCode.OK to { body<AnnexCRequestResponse>() } }
                }) { request ->
                    val session = getSessionOrThrow(request.sessionId)

                    require(!isExpired(session)) { "Session expired" }

                    val annexRequest = AnnexCRequestBuilder.build(
                        docType = session.docType,
                        requestedElements = session.requestedElements,
                        nonce = session.nonce,
                        recipientPublicKey = session.recipientPublicKey,
                        intentToRetain = request.intentToRetain,
                    )

                    session.deviceRequestB64 = annexRequest.deviceRequestB64
                    session.encryptionInfoB64 = annexRequest.encryptionInfoB64
                    session.status = AnnexCSessionStatus.request_built

                    call.respond(
                        AnnexCRequestResponse(
                            protocol = AnnexC.PROTOCOL,
                            data = AnnexCRequestResponse.Data(
                                deviceRequest = annexRequest.deviceRequestB64,
                                encryptionInfo = annexRequest.encryptionInfoB64
                            ),
                            meta = AnnexCRequestResponse.Meta(
                                sessionId = session.id,
                                expiresAt = session.expiresAt.toString()
                            )
                        )
                    )
                }

                post<AnnexCResponseRequest>("response", {
                    summary = "Store wallet response and start async decrypt/verify"
                    response { HttpStatusCode.OK to { body<AnnexCResponseAck>() } }
                }) { request ->
                    val session = getSessionOrThrow(request.sessionId)
                    require(!isExpired(session)) { "Session expired" }

                    session.encryptedResponseB64 = request.response
                    session.status = AnnexCSessionStatus.response_received
                    session.error = null

                    processingScope.launch {
                        try {
                            session.status = AnnexCSessionStatus.processing
                            val encryptionInfoB64 = requireNotNull(session.encryptionInfoB64) {
                                "Missing encryptionInfo (call /annex-c/request first)"
                            }
                            val deviceResponseBytes = AnnexCResponseVerifierJvm.decryptToDeviceResponse(
                                encryptedResponseB64 = request.response,
                                encryptionInfoB64 = encryptionInfoB64,
                                origin = session.origin,
                                recipientPrivateKey = session.recipientPrivateKey
                            )
                            session.deviceResponseCborBytes = deviceResponseBytes
                            session.status = AnnexCSessionStatus.decrypted

                            val sessionTranscript = AnnexCTranscriptBuilder.buildSessionTranscript(
                                encryptionInfoB64 = encryptionInfoB64,
                                origin = session.origin
                            )

                            val deviceResponseB64 = Base64UrlNoPad.encode(deviceResponseBytes)
                            val document = MdocParser.parseToDocument(deviceResponseB64)

                            session.verificationResult = MdocVerifier.verify(document, sessionTranscript)
                            session.status = if (session.verificationResult?.valid == true) {
                                AnnexCSessionStatus.verified
                            } else {
                                AnnexCSessionStatus.failed
                            }
                        } catch (e: Exception) {
                            session.error = e.message ?: e.toString()
                            session.status = AnnexCSessionStatus.failed
                            log.warn(e) { "Annex C processing failed for session ${session.id}" }
                        }
                    }

                    call.respond(AnnexCResponseAck())
                }

                get("info", {
                    summary = "Get Annex C session info"
                    request { queryParameter<String>("sessionId") }
                    response { HttpStatusCode.OK to { body<AnnexCInfoResponse>() } }
                }) {
                    val sessionId = call.request.queryParameters["sessionId"]
                        ?: throw IllegalArgumentException("Missing query parameter: sessionId")
                    val session = getSessionOrThrow(sessionId)

                    if (isExpired(session) && session.status != AnnexCSessionStatus.verified && session.status != AnnexCSessionStatus.failed) {
                        session.status = AnnexCSessionStatus.expired
                    }

                    val deviceResponseB64 = session.deviceResponseCborBytes?.let { Base64UrlNoPad.encode(it) }

                    call.respond(
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
                    )
                }
            }
        }
    }

    private fun getSessionOrThrow(sessionId: String): AnnexCSession =
        sessions[sessionId] ?: throw IllegalArgumentException("Unknown Annex C session id")

    private fun isExpired(session: AnnexCSession): Boolean = Clock.System.now() > session.expiresAt
}
