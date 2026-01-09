@file:OptIn(ExperimentalUuidApi::class)

package id.walt.openid4vp.verifier.annexc

import id.walt.credentials.presentations.formats.MsoMdocPresentation
import id.walt.iso18013.annexc.*
import id.walt.iso18013.annexc.cbor.Base64UrlNoPad
import id.walt.ktornotifications.KtorNotifications.notifySessionUpdate
import id.walt.ktornotifications.core.KtorSessionUpdate
import id.walt.mdoc.parser.MdocParser
import id.walt.mdoc.verification.MdocVerifier
import id.walt.openid4vp.verifier.annexc.openapi.AnnexCOpenApi
import id.walt.openid4vp.verifier.data.Verification2Session
import id.walt.openid4vp.verifier.handlers.vpresponse.Verifier2SessionCredentialPolicyValidation
import id.walt.openid4vp.verifier.verification2.Verifier2PolicyResults
import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.policies2.vp.policies.VPPolicyList
import id.walt.policies2.vp.policies.VPVerificationPolicyManager
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.klogging.logger
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val log = logger("AnnexCService")

object AnnexCService {
    private const val ANNEX_C = "annex-c"
    internal const val FLOW_TYPE = "dc_api-annex-c"
    private const val CLEANUP_INTERVAL_SECONDS = 60L
    private val terminalRetention = 5.minutes
    private val includeDeviceResponseCbor =
        System.getenv("ANNEXC_INCLUDE_DEVICE_RESPONSE")?.toBoolean() ?: false

    private val random = SecureRandom()
    private val processingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    internal val sessions = ConcurrentHashMap<String, AnnexCSession>()
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
        val policies: Verification2Session.DefinedVerificationPolicies = Verification2Session.DefinedVerificationPolicies(),
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
    ) {
        @Serializable
        data class Data(
            @SerialName("deviceRequest")
            val deviceRequest: String,
            @SerialName("encryptionInfo")
            val encryptionInfo: String,
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
        val flowType: String,
        val origin: String,
        val expiresAt: String,
        val docType: String,
        val requestedElements: Map<String, List<String>>,
        val policies: Verification2Session.DefinedVerificationPolicies = Verification2Session.DefinedVerificationPolicies(),

        val deviceRequest: String? = null,
        val encryptionInfo: String? = null,
        val encryptedResponse: String? = null,

        val deviceResponseCbor: String? = null,
        val mdocVerificationResult: id.walt.mdoc.verification.VerificationResult? = null,
        val policyResults: Verifier2PolicyResults? = null,
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
        val flowType: String = FLOW_TYPE,
        val origin: String,
        val docType: String,
        val requestedElements: Map<String, List<String>>,
        val policies: Verification2Session.DefinedVerificationPolicies,
        val nonce: ByteArray,
        val recipientPrivateKey: ByteArray,
        val recipientPublicKey: id.walt.cose.CoseKey,
        var status: AnnexCSessionStatus = AnnexCSessionStatus.created,
        var deviceRequestB64: String? = null,
        var encryptionInfoB64: String? = null,
        var encryptedResponseB64: String? = null,
        var deviceResponseCborBytes: ByteArray? = null,
        var verificationResult: id.walt.mdoc.verification.VerificationResult? = null,
        var policyResults: Verifier2PolicyResults? = null,
        var error: String? = null,
        var completedAt: Instant? = null,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as AnnexCSession

            if (id != other.id) return false
            if (createdAt != other.createdAt) return false
            if (expiresAt != other.expiresAt) return false
            if (flowType != other.flowType) return false
            if (origin != other.origin) return false
            if (docType != other.docType) return false
            if (requestedElements != other.requestedElements) return false
            if (policies != other.policies) return false
            if (!nonce.contentEquals(other.nonce)) return false
            if (!recipientPrivateKey.contentEquals(other.recipientPrivateKey)) return false
            if (recipientPublicKey != other.recipientPublicKey) return false
            if (status != other.status) return false
            if (deviceRequestB64 != other.deviceRequestB64) return false
            if (encryptionInfoB64 != other.encryptionInfoB64) return false
            if (encryptedResponseB64 != other.encryptedResponseB64) return false
            if (!deviceResponseCborBytes.contentEquals(other.deviceResponseCborBytes)) return false
            if (verificationResult != other.verificationResult) return false
            if (policyResults != other.policyResults) return false
            if (error != other.error) return false
            if (completedAt != other.completedAt) return false

            return true
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + createdAt.hashCode()
            result = 31 * result + expiresAt.hashCode()
            result = 31 * result + flowType.hashCode()
            result = 31 * result + origin.hashCode()
            result = 31 * result + docType.hashCode()
            result = 31 * result + requestedElements.hashCode()
            result = 31 * result + policies.hashCode()
            result = 31 * result + nonce.contentHashCode()
            result = 31 * result + recipientPrivateKey.contentHashCode()
            result = 31 * result + recipientPublicKey.hashCode()
            result = 31 * result + status.hashCode()
            result = 31 * result + (deviceRequestB64?.hashCode() ?: 0)
            result = 31 * result + (encryptionInfoB64?.hashCode() ?: 0)
            result = 31 * result + (encryptedResponseB64?.hashCode() ?: 0)
            result = 31 * result + (deviceResponseCborBytes?.contentHashCode() ?: 0)
            result = 31 * result + (verificationResult?.hashCode() ?: 0)
            result = 31 * result + (policyResults?.hashCode() ?: 0)
            result = 31 * result + (error?.hashCode() ?: 0)
            result = 31 * result + (completedAt?.hashCode() ?: 0)
            return result
        }
    }

    private data class AnnexCProcessingContext(
        val sessionId: String,
        val origin: String,
        val encryptionInfoB64: String,
        val recipientPrivateKey: ByteArray,
        val responseB64: String,
    )

    private const val EVENT_SESSION_UPDATED = "annexc_session_updated"
    private const val ANNEX_C_QUERY_ID = "annex_c"

    internal fun createSession(
        docType: String,
        requestedElements: Map<String, List<String>>,
        policies: Verification2Session.DefinedVerificationPolicies,
        origin: String,
        ttlSeconds: Long?
    ): AnnexCSession {
        require(ttlSeconds == null || ttlSeconds > 0) { "ttlSeconds must be > 0" }
        val ttl = (ttlSeconds?.seconds ?: 5.minutes)
        val now = Clock.System.now()
        val expiresAt = now + ttl

        val nonce = ByteArray(16).also { random.nextBytes(it) }
        val keyPair = AnnexCHpkeKeyGeneratorJvm.generateRecipientKeyPair()

        val effectivePolicies = applyDefaultPolicies(policies)

        return AnnexCSession(
            id = Uuid.random().toString(),
            createdAt = now,
            expiresAt = expiresAt,
            origin = origin,
            docType = docType,
            requestedElements = requestedElements,
            policies = effectivePolicies,
            nonce = nonce,
            recipientPrivateKey = keyPair.recipientPrivateKey,
            recipientPublicKey = keyPair.recipientPublicKey,
        ).also { session ->
            sessions[session.id] = session
            sessionLocks[session.id] = Mutex()
        }
    }

    internal suspend fun buildRequest(session: AnnexCSession, intentToRetain: Boolean): AnnexCRequestResponse {
        val lock = getSessionLock(session.id)
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
                intentToRetain = intentToRetain,
            ).also {
                session.deviceRequestB64 = it.deviceRequestB64
                session.encryptionInfoB64 = it.encryptionInfoB64
                session.status = AnnexCSessionStatus.request_built
            }
        }

        return AnnexCRequestResponse(
            protocol = AnnexC.PROTOCOL,
            data = AnnexCRequestResponse.Data(
                deviceRequest = annexRequest.deviceRequestB64,
                encryptionInfo = annexRequest.encryptionInfoB64
            )
        )
    }

    internal suspend fun acceptResponse(session: AnnexCSession, encryptedResponseB64: String): AnnexCResponseAck {
        val lock = getSessionLock(session.id)
        val context = lock.withLock {
            if (isExpired(session)) {
                session.status = AnnexCSessionStatus.expired
                markCompleted(session)
                throw IllegalArgumentException("Session expired")
            }

            val encryptionInfoB64 = requireNotNull(session.encryptionInfoB64) {
                "Missing encryptionInfo (call /annex-c/request first)"
            }

            session.encryptedResponseB64 = encryptedResponseB64
            session.status = AnnexCSessionStatus.response_received
            session.error = null

            AnnexCProcessingContext(
                sessionId = session.id,
                origin = session.origin,
                encryptionInfoB64 = encryptionInfoB64,
                recipientPrivateKey = session.recipientPrivateKey.copyOf(),
                responseB64 = encryptedResponseB64
            )
        }

        publishSessionUpdate(session)

        processingScope.launch {
            processResponse(context)
        }

        return AnnexCResponseAck()
    }

    internal suspend fun buildInfoResponse(session: AnnexCSession): AnnexCInfoResponse {
        val lock = getSessionLock(session.id)
        return lock.withLock {
            if (isExpired(session) && session.status != AnnexCSessionStatus.verified && session.status != AnnexCSessionStatus.failed) {
                session.status = AnnexCSessionStatus.expired
                markCompleted(session)
            }

            val deviceResponseB64 =
                if (includeDeviceResponseCbor) session.deviceResponseCborBytes?.let { Base64UrlNoPad.encode(it) } else null

            AnnexCInfoResponse(
                sessionId = session.id,
                status = session.status,
                flowType = session.flowType,
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
                policyResults = session.policyResults,
                error = session.error
            )
        }
    }

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
            publishSessionUpdate(session)

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
            publishSessionUpdate(session)

            val sessionTranscript = AnnexCTranscriptBuilder.buildSessionTranscript(
                encryptionInfoB64 = context.encryptionInfoB64,
                origin = context.origin
            )

            val deviceResponseB64 = Base64UrlNoPad.encode(deviceResponseBytes)
            val document = MdocParser.parseToDocument(deviceResponseB64)
            val verificationResult = MdocVerifier.verify(document, sessionTranscript)
            val policyResults = runPolicyValidation(session, deviceResponseB64)

            lock.withLock {
                session.verificationResult = verificationResult
                session.policyResults = policyResults
                val mdocValid = verificationResult.valid
                val policiesValid = policyResults.overallSuccess != false
                session.status = if (mdocValid && policiesValid) {
                    AnnexCSessionStatus.verified
                } else {
                    AnnexCSessionStatus.failed
                }
                markCompleted(session)
                wipeSensitive(session)
            }
            publishSessionUpdate(session)
        } catch (e: Exception) {
            lock.withLock {
                session.error = e.message ?: e.toString()
                session.status = AnnexCSessionStatus.failed
                markCompleted(session)
                wipeSensitive(session)
            }
            log.warn(e) { "Annex C processing failed for session ${context.sessionId}" }
            publishSessionUpdate(session)
        }
    }

    private suspend fun runPolicyValidation(session: AnnexCSession, deviceResponseB64: String): Verifier2PolicyResults {
        val presentation = MsoMdocPresentation.parse(deviceResponseB64).getOrThrow()
        val credentials = mapOf(ANNEX_C_QUERY_ID to listOf(presentation.mdoc))
        val credentialResults = Verifier2SessionCredentialPolicyValidation.validateCredentialPolicies(
            policies = session.policies,
            validatedCredentials = credentials
        )
        return Verifier2PolicyResults(
            vpPolicies = emptyMap(),
            vcPolicies = credentialResults.vcPolicies,
            specificVcPolicies = credentialResults.specificVcPolicies
        )
    }

    private fun applyDefaultPolicies(
        policies: Verification2Session.DefinedVerificationPolicies
    ): Verification2Session.DefinedVerificationPolicies {
        val vpPolicies = policies.vp_policies ?: VPPolicyList(
            jwtVcJson = VPVerificationPolicyManager.defaultJwtVcJsonPolicies,
            dcSdJwt = VPVerificationPolicyManager.defaultDcSdJwtPolicies,
            msoMdoc = VPVerificationPolicyManager.defaultMsoMdocPolicies
        )
        val vcPolicies = policies.vc_policies ?: VCPolicyList(
            policies = listOf(CredentialSignaturePolicy())
        )
        return Verification2Session.DefinedVerificationPolicies(
            vp_policies = vpPolicies,
            vc_policies = vcPolicies,
            specific_vc_policies = policies.specific_vc_policies
        )
    }

    internal suspend fun publishSessionUpdate(session: AnnexCSession) {
        val update = KtorSessionUpdate(
            target = session.id,
            event = EVENT_SESSION_UPDATED,
            session = Json.encodeToJsonElement(buildInfoResponse(session)).jsonObject
        )
        update.notifySessionUpdate(session.id, null)
    }

    fun Route.registerRoute() {
        route(ANNEX_C) {
            route("", { tags("Annex C (ISO 18013-7)") }) {
                post<AnnexCCreateRequest>("create", AnnexCOpenApi.createDocs) { request ->
                    val session = createSession(
                        docType = request.docType,
                        requestedElements = request.requestedElements,
                        policies = request.policies,
                        origin = request.origin,
                        ttlSeconds = request.ttlSeconds
                    )
                    publishSessionUpdate(session)
                    call.respond(AnnexCCreateResponse(sessionId = session.id, expiresAt = session.expiresAt.toString()))
                }

                post<AnnexCRequestRequest>("request", AnnexCOpenApi.requestDocs) { request ->
                    val session = sessions[request.sessionId]
                        ?: return@post call.respond(HttpStatusCode.NotFound, "Unknown Annex C session id")
                    val response = buildRequest(session, request.intentToRetain)
                    publishSessionUpdate(session)
                    call.respond(response)
                }

                post<AnnexCResponseRequest>("response", AnnexCOpenApi.responseDocs) { request ->
                    val session = sessions[request.sessionId]
                        ?: return@post call.respond(HttpStatusCode.NotFound, "Unknown Annex C session id")
                    val ack = acceptResponse(session, request.response)
                    call.respond(ack)
                }

                get("info", AnnexCOpenApi.infoDocs) {
                    val sessionId = call.request.queryParameters["sessionId"]
                        ?: throw IllegalArgumentException("Missing query parameter: sessionId")
                    val session = sessions[sessionId]
                        ?: return@get call.respond(HttpStatusCode.NotFound, "Unknown Annex C session id")
                    call.respond(buildInfoResponse(session))
                }
            }
        }
    }

    private fun isExpired(session: AnnexCSession): Boolean = Clock.System.now() > session.expiresAt

    private fun getSessionLock(sessionId: String): Mutex =
        sessionLocks.computeIfAbsent(sessionId) { Mutex() }

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
