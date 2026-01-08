package id.walt.openid4vp.verifier.openapi

import id.walt.iso18013.annexc.AnnexC
import id.walt.openid4vp.verifier.AnnexCResponsePayload
import id.walt.openid4vp.verifier.UnifiedEnvelope
import id.walt.openid4vp.verifier.annexc.AnnexCService
import id.walt.openid4vp.verifier.data.Verification2Session
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

object VerificationSessionUnifiedOpenApi {

    private const val SESSION_ID = "verification-session"

    private val annexCRequestExample = AnnexCService.AnnexCRequestResponse(
        protocol = AnnexC.PROTOCOL,
        data = AnnexCService.AnnexCRequestResponse.Data(
            deviceRequest = "<b64url(cbor(DeviceRequest))>",
            encryptionInfo = "<b64url(cbor(EncryptionInfo))>",
        )
    )

    private val annexCInfoExample = AnnexCService.AnnexCInfoResponse(
        sessionId = "b4c2a5a4-2a53-4f39-a9d6-3df4f93c2d4b",
        status = AnnexCService.AnnexCSessionStatus.processing,
        flowType = AnnexCService.FLOW_TYPE,
        origin = "https://digital-credentials.walt.id",
        expiresAt = "2025-01-01T00:05:00Z",
        docType = "org.iso.18013.5.1.mDL",
        requestedElements = mapOf("org.iso.18013.5.1" to listOf("age_over_18")),
        policies = Verification2Session.DefinedVerificationPolicies(),
        deviceRequest = annexCRequestExample.data.deviceRequest,
        encryptionInfo = annexCRequestExample.data.encryptionInfo,
        encryptedResponse = "<b64url(cbor(EncryptedResponse))>",
        deviceResponseCbor = null,
        mdocVerificationResult = null,
        policyResults = null,
        error = null
    )

    private val annexCEnvelopeRequestExample = UnifiedEnvelope(
        sessionId = annexCInfoExample.sessionId,
        flowType = AnnexCService.FLOW_TYPE,
        data = Json.encodeToJsonElement(annexCRequestExample),
        error = null
    )

    private val annexCEnvelopeInfoExample = UnifiedEnvelope(
        sessionId = annexCInfoExample.sessionId,
        flowType = AnnexCService.FLOW_TYPE,
        data = Json.encodeToJsonElement(annexCInfoExample),
        error = null
    )

    private val annexCEnvelopeAckExample = UnifiedEnvelope(
        sessionId = annexCInfoExample.sessionId,
        flowType = AnnexCService.FLOW_TYPE,
        data = Json.encodeToJsonElement(AnnexCService.AnnexCResponseAck()),
        error = null
    )

    private val annexCResponsePayloadExample = AnnexCResponsePayload(
        response = "<b64url(cbor(EncryptedResponse))>"
    )

    val requestDocs: RouteConfig.() -> Unit = {
        summary = "Get session request payload"
        description = """
            Returns the request payload for the session.
            - OpenID4VP flows return Authorization Request data (JWT, JSON, or raw object).
            - Annex C flows return the DC API protocol object (DeviceRequest + EncryptionInfo).
            
            To receive the unified v2 envelope, set `?envelope=true`.
        """.trimIndent()

        request {
            pathParameter<String>(SESSION_ID)
            queryParameter<Boolean>("envelope") {
                description = "Set to true to enable the v2 response envelope"
            }
            queryParameter<Boolean>("intentToRetain") {
                description = "Annex C only: set intentToRetain in DeviceRequest items"
            }
        }

        response {
            HttpStatusCode.OK to {
                body<UnifiedEnvelope> {
                    required = true
                    example("Annex C (v2 envelope)") { value = annexCEnvelopeRequestExample }
                }
            }
        }
    }

    val responseDocs: RouteConfig.() -> Unit = {
        summary = "Submit session response"
        description = """
            Submits the wallet response for the session.
            - OpenID4VP flows use `application/x-www-form-urlencoded` (vp_token, state, or response).
            - Annex C flows use JSON with `response` = EncryptedResponse (base64url CBOR).
            
            To receive the unified v2 envelope, set `?envelope=true`.
        """.trimIndent()

        request {
            pathParameter<String>(SESSION_ID)
            queryParameter<Boolean>("envelope") {
                description = "Set to true to enable the v2 response envelope"
            }
            body<AnnexCResponsePayload> {
                description = "Annex C response payload (JSON)"
                example("Annex C response") { value = annexCResponsePayloadExample }
            }
        }

        response {
            HttpStatusCode.OK to {
                body<UnifiedEnvelope> {
                    required = true
                    example("Annex C ack (v2 envelope)") { value = annexCEnvelopeAckExample }
                }
            }
        }
    }

    val infoDocs: RouteConfig.() -> Unit = {
        summary = "View data of existing verification session"
        description = """
            Returns the current state of a verification session.
            - OpenID4VP flows return `Verification2Session`.
            - Annex C flows return `AnnexCInfoResponse`.
            
            To receive the unified v2 envelope, set `?envelope=true`.
        """.trimIndent()

        request {
            pathParameter<String>(SESSION_ID)
            queryParameter<Boolean>("envelope") {
                description = "Set to true to enable the v2 response envelope"
            }
        }

        response {
            HttpStatusCode.OK to {
                body<UnifiedEnvelope> {
                    required = true
                    example("Annex C info (v2 envelope)") { value = annexCEnvelopeInfoExample }
                }
            }
        }
    }

    val eventsDocs: RouteConfig.() -> Unit = {
        summary = "Receive update events via SSE about the verification session"
        description = """
            Server-sent events for session updates.
            When v2 envelope is enabled, each event's `session` field contains the unified envelope.
        """.trimIndent()

        request {
            pathParameter<String>(SESSION_ID)
            queryParameter<Boolean>("envelope") {
                description = "Set to true to enable the v2 response envelope"
            }
        }
    }
}
