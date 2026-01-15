package id.walt.openid4vp.verifier.annexc.openapi

import id.walt.iso18013.annexc.AnnexC
import id.walt.openid4vp.verifier.annexc.AnnexCService
import id.walt.openid4vp.verifier.data.Verification2Session
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.*

object AnnexCOpenApi {

    private const val DOCS_URL_PLACEHOLDER =
        "<a href='https://docs.walt.id/community-stack/verifier/getting-started'>the docs</a>"

    private val createRequestExample = AnnexCService.AnnexCCreateRequest(
        docType = "org.iso.18013.5.1.mDL",
        requestedElements = mapOf("org.iso.18013.5.1" to listOf("age_over_18")),
        policies = Verification2Session.DefinedVerificationPolicies(),
        origin = "https://digital-credentials.walt.id",
        ttlSeconds = 300,
    )

    private val createResponseExample = AnnexCService.AnnexCCreateResponse(
        sessionId = "b4c2a5a4-2a53-4f39-a9d6-3df4f93c2d4b",
        expiresAt = "2025-01-01T00:05:00Z",
    )

    private val requestRequestExample = AnnexCService.AnnexCRequestRequest(
        sessionId = createResponseExample.sessionId,
        intentToRetain = false,
    )

    private val requestResponseExample = AnnexCService.AnnexCRequestResponse(
        protocol = AnnexC.PROTOCOL,
        data = AnnexCService.AnnexCRequestResponse.Data(
            deviceRequest = "<b64url(cbor(DeviceRequest))>",
            encryptionInfo = "<b64url(cbor(EncryptionInfo))>",
        )
//        ,meta = AnnexCService.AnnexCRequestResponse.Meta(
//            sessionId = createResponseExample.sessionId,
//            expiresAt = createResponseExample.expiresAt,
//        )
    )

    private val responseRequestExample = AnnexCService.AnnexCResponseRequest(
        sessionId = createResponseExample.sessionId,
        response = "<b64url(cbor(EncryptedResponse))>",
    )

    private val responseAckExample = AnnexCService.AnnexCResponseAck(status = "received")

    private val infoResponseExample = AnnexCService.AnnexCInfoResponse(
        sessionId = createResponseExample.sessionId,
        status = AnnexCService.AnnexCSessionStatus.processing,
        flowType = AnnexCService.FLOW_TYPE,
        origin = createRequestExample.origin,
        expiresAt = createResponseExample.expiresAt,
        docType = createRequestExample.docType,
        requestedElements = createRequestExample.requestedElements,
        policies = createRequestExample.policies,
        deviceRequest = requestResponseExample.data.deviceRequest,
        encryptionInfo = requestResponseExample.data.encryptionInfo,
        encryptedResponse = responseRequestExample.response,
        deviceResponseCbor = null,
        mdocVerificationResult = null,
        error = null,
    )

    val createDocs: RouteConfig.() -> Unit = {
        summary = "Create Annex C session"
        description = """
            Creates a short-lived Annex C session to be used with the Digital Credentials API (DC API).
            The backend generates:
            - a random `nonce`
            - an HPKE recipient keypair (skR/pkR)
            
            The subsequent `/annex-c/request` call uses the stored nonce/public key to build
            `{ deviceRequest, encryptionInfo }` for the browser-facing DC API call.
            
            For more information, see: $DOCS_URL_PLACEHOLDER
        """.trimIndent()

        request {
            body<AnnexCService.AnnexCCreateRequest> {
                required = true
                example("Create Annex C session") { value = createRequestExample }
            }
        }
        response {
            HttpStatusCode.OK to {
                body<AnnexCService.AnnexCCreateResponse> {
                    required = true
                    example("Session created") { value = createResponseExample }
                }
            }
        }
    }

    val requestDocs: RouteConfig.() -> Unit = {
        summary = "Build DeviceRequest + EncryptionInfo"
        description = """
            Builds the values required by the browser-facing DC API request:
            - `deviceRequest` = base64url(no-pad) CBOR `DeviceRequest`
            - `encryptionInfo` = base64url(no-pad) CBOR `EncryptionInfo`
        """.trimIndent()

        request {
            body<AnnexCService.AnnexCRequestRequest> {
                required = true
                example("Build request payload") { value = requestRequestExample }
            }
        }
        response {
            HttpStatusCode.OK to {
                body<AnnexCService.AnnexCRequestResponse> {
                    required = true
                    example("DC API protocol object") { value = requestResponseExample }
                }
            }
        }
    }

    val responseDocs: RouteConfig.() -> Unit = {
        summary = "Submit wallet EncryptedResponse"
        description = """
            Stores the wallet `response` (base64url CBOR `EncryptedResponse`) and starts async processing:
            - reconstruct SessionTranscript/HPKE info from `(origin, encryptionInfo)`
            - HPKE decrypt to `DeviceResponse` CBOR bytes
            - run mdoc verification
        """.trimIndent()

        request {
            body<AnnexCService.AnnexCResponseRequest> {
                required = true
                example("Submit EncryptedResponse") { value = responseRequestExample }
            }
        }
        response {
            HttpStatusCode.OK to {
                body<AnnexCService.AnnexCResponseAck> {
                    required = true
                    example("Acknowledgement") { value = responseAckExample }
                }
            }
        }
    }

    val infoDocs: RouteConfig.() -> Unit = {
        summary = "Get Annex C session status"
        request {
            queryParameter<String>("sessionId") {
                required = true
                example("Session ID") { value = createResponseExample.sessionId }
            }
        }
        response {
            HttpStatusCode.OK to {
                body<AnnexCService.AnnexCInfoResponse> {
                    required = true
                    example("Session state") { value = infoResponseExample }
                }
            }
        }
    }
}
