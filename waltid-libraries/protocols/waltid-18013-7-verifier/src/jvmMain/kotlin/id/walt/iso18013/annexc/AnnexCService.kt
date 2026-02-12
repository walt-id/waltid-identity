@file:OptIn(ExperimentalUuidApi::class)

package id.walt.iso18013.annexc

import id.walt.cose.CoseKey
import id.walt.cose.JWKKeyCoseTransform.getCosePublicKey
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.iso18013.annexc.protocol.AnnexCInfoResponse
import id.walt.iso18013.annexc.protocol.AnnexCRequestResponse
import id.walt.iso18013.annexc.protocol.AnnexCResponseAck
import id.walt.iso18013.annexc.protocol.AnnexCSessionStatus
import id.walt.mdoc.parser.MdocParser
import id.walt.mdoc.verification.MdocVerifier
import id.walt.mdoc.verification.VerificationResult
import io.github.oshai.kotlinlogging.KotlinLogging.logger
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
    private val random = SecureRandom()

    internal val sessions = ConcurrentHashMap<String, AnnexCSession>()



    data class AnnexCSession(
        val id: String, // DUPLICATE
        val createdAt: Instant, // DUPLICATE
        val expiresAt: Instant, // DUPLICATE

        // setup
        val origin: String,
        val docType: String,
        val requestedElements: Map<String, List<String>>,


     //   val policies: Verification2Session.DefinedVerificationPolicies, // DUPLICATE
        val nonce: ByteArray,
        val recipientPrivateKey: DirectSerializedKey, // DUPLICATE
        val recipientPublicKey: CoseKey,
        var status: AnnexCSessionStatus = AnnexCSessionStatus.created,
        var deviceRequestB64: String? = null,
        var encryptionInfoB64: String? = null,
        var encryptedResponseB64: String? = null,
        var deviceResponseCborBytes: ByteArray? = null,
        var verificationResult: VerificationResult? = null, // DUPLICATE
      //  var policyResults: Verifier2PolicyResults? = null, // DUPLICATE
        var error: String? = null, // DUPLICATE
        var completedAt: Instant? = null, // DUPLICATE
    ) {

    }

    private data class AnnexCProcessingContext(
        val sessionId: String,
        val origin: String,
        val encryptionInfoB64: String,
        val recipientPrivateKey: JWKKey,
        val responseB64: String,
    )

    internal suspend fun createSession(
        docType: String,
        requestedElements: Map<String, List<String>>,
     //   policies: Verification2Session.DefinedVerificationPolicies,
        origin: String,
        ttlSeconds: Long?
    ): AnnexCSession {
        require(ttlSeconds == null || ttlSeconds > 0) { "ttlSeconds must be > 0" }
        val ttl = (ttlSeconds?.seconds ?: 5.minutes)
        val now = Clock.System.now()
        val expiresAt = now + ttl

        val nonce = ByteArray(16).also { random.nextBytes(it) }
        val key = JWKKey.generate(KeyType.secp256r1)

//        val effectivePolicies = applyDefaultPolicies(policies)

        return AnnexCSession(
            id = Uuid.random().toString(), // DUPLICATE
            createdAt = now, // DUPLICATE
            expiresAt = expiresAt, // DUPLICATE
            origin = origin,
            docType = docType,
            requestedElements = requestedElements,
//            policies = effectivePolicies, // DUPLICATE
            nonce = nonce,
            recipientPrivateKey = DirectSerializedKey(key), // DUPLICATE
            recipientPublicKey = key.getCosePublicKey(), // CAN BE OMITTED
        ).also { session ->
            sessions[session.id] = session
        }
    }

    internal fun buildRequest(session: AnnexCSession, intentToRetain: Boolean): AnnexCRequestResponse {
   /*     if (isExpired(session)) {
            session.status = AnnexCSessionStatus.expired
            markCompleted(session)
            throw IllegalArgumentException("Session expired")
        }*/

        val annexRequest = AnnexCRequestBuilder.build(
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

        return AnnexCRequestResponse(
            protocol = AnnexC.PROTOCOL,
            data = AnnexCRequestResponse.Data(
                deviceRequest = annexRequest.deviceRequestB64,
                encryptionInfo = annexRequest.encryptionInfoB64
            )
        )
    }

    internal suspend fun acceptResponse(session: AnnexCSession, encryptedResponseB64: String): AnnexCResponseAck {
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

        val context = AnnexCProcessingContext(
            sessionId = session.id,
            origin = session.origin,
            encryptionInfoB64 = encryptionInfoB64,
            recipientPrivateKey = session.recipientPrivateKey.key as? JWKKey ?: throw IllegalArgumentException("Only works with JWK Key!"),
            responseB64 = encryptedResponseB64
        )

//        publishSessionUpdate(session)

        processResponse(context)

        return AnnexCResponseAck()
    }

    internal fun buildInfoResponse(session: AnnexCSession): AnnexCInfoResponse {
        if (isExpired(session) && session.status != AnnexCSessionStatus.verified && session.status != AnnexCSessionStatus.failed) {
            session.status = AnnexCSessionStatus.expired
            markCompleted(session)
        }

        val deviceResponseB64 =
            session.deviceResponseCborBytes?.encodeToBase64Url()

        return AnnexCInfoResponse(
            sessionId = session.id,
            status = session.status,
            origin = session.origin,
            expiresAt = session.expiresAt.toString(),
            docType = session.docType,
            requestedElements = session.requestedElements,
       //     policies = session.policies,
            deviceRequest = session.deviceRequestB64,
            encryptionInfo = session.encryptionInfoB64,
            encryptedResponse = session.encryptedResponseB64,
            deviceResponseCbor = deviceResponseB64,
            //mdocVerificationResult = session.verificationResult,
        //    policyResults = session.policyResults,
            error = session.error
        )
    }

    private suspend fun processResponse(context: AnnexCProcessingContext) {
        val session = sessions[context.sessionId] ?: return

        try {
            if (isExpired(session)) {
                session.status = AnnexCSessionStatus.expired
                markCompleted(session)
                return
            }
            session.status = AnnexCSessionStatus.processing
//            publishSessionUpdate(session)

            val deviceResponseBytes = AnnexCResponseVerifier.decryptToDeviceResponse(
                encryptedResponseB64 = context.responseB64,
                encryptionInfoB64 = context.encryptionInfoB64,
                origin = context.origin,
                recipientPrivateKey = context.recipientPrivateKey
            )

            session.deviceResponseCborBytes = deviceResponseBytes
            session.status = AnnexCSessionStatus.decrypted
//            publishSessionUpdate(session)

            val sessionTranscript = AnnexCTranscriptBuilder.buildSessionTranscript(
                encryptionInfoB64 = context.encryptionInfoB64,
                origin = context.origin
            )

            val deviceResponseB64 = deviceResponseBytes.encodeToBase64Url()
            val document = MdocParser.parseToDocument(deviceResponseB64)
            val verificationResult = MdocVerifier.verify(document, sessionTranscript)
//            val policyResults = runPolicyValidation(session, deviceResponseB64)

            session.verificationResult = verificationResult
          //  session.policyResults = policyResults
            val mdocValid = verificationResult.valid
        //    val policiesValid = policyResults.overallSuccess != false
     /*       session.status = if (mdocValid && policiesValid) {
                AnnexCSessionStatus.verified
            } else {
                AnnexCSessionStatus.failed
            }*/
            markCompleted(session)
//            publishSessionUpdate(session)
        } catch (e: Exception) {
            session.error = e.message ?: e.toString()
            session.status = AnnexCSessionStatus.failed
            markCompleted(session)
       //     log.warn(e) { "Annex C processing failed for session ${context.sessionId}" }
//            publishSessionUpdate(session)
        }
    }

   /* private suspend fun runPolicyValidation(session: AnnexCSession, deviceResponseB64: String): Verifier2PolicyResults {
        val presentation = MsoMdocPresentation.parse(deviceResponseB64).getOrThrow()
        val credentials = mapOf("annex_c" to listOf(presentation.mdoc))
        val credentialResults = Verifier2SessionCredentialPolicyValidation.validateCredentialPolicies(
            policies = session.policies,
            validatedCredentials = credentials
        )
        return Verifier2PolicyResults(
            vpPolicies = emptyMap(),
            vcPolicies = credentialResults.vcPolicies,
            specificVcPolicies = credentialResults.specificVcPolicies
        )
    }*/

  /*  private fun applyDefaultPolicies(
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
            event = "annexc_session_updated",
            session = Json.encodeToJsonElement(buildInfoResponse(session)).jsonObject
        )
        update.notifySessionUpdate(session.id, null)
    }*/

    private fun isExpired(session: AnnexCSession): Boolean = Clock.System.now() > session.expiresAt

    private fun markCompleted(session: AnnexCSession) {
        if (session.completedAt == null) {
            session.completedAt = Clock.System.now()
        }
    }

}
