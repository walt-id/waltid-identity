package id.walt.verifier2.openapi

import id.walt.verifier2.data.ExternalReaderDeviceResponse
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.Verification2Session
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ExternalReaderDeviceResponseOpenApi {
    private val qr = ExternalReaderDeviceResponse(
        flowType = "iso_18013_5_external_reader",
        core = GeneralFlowConfig(),
        deviceResponse = "<base64 DeviceResponse CBOR>",
        deviceEngagement = "<base64 exact DeviceEngagement CBOR>",
        eReaderKey = "<base64 exact public COSE_Key CBOR>",
        handover = JsonPrimitive("qr"),
    )

    val docs: RouteConfig.() -> Unit = {
        summary = "Verify a decrypted ISO 18013-5 response from an external reader"
        description = """
            Requires the proximity-verifier feature flag (off by default). Uses the same authentication as session creation.
            The reader owns engagement, its private key, session encryption, DeviceRequest and termination.
            Submit the decrypted DeviceResponse CBOR and exact transcript inputs as standard base64, without re-encoding them.
            No session is created ahead of the exchange. Every authenticated submission creates a new session, including replays.
            reattemptable does not deduplicate separate calls; each returned session is completed and accepts no further attempts.
            Both SUCCESSFUL and FAILED outcomes return 200 with the same public session view as info.
            A malformed request returns 400 and creates no session. Cryptographic failures are stored FAILED outcomes.
            core_flow carries dcql_query or requestedElements, policies, notifications.webhook and retention overrides.
            The reader makes the access decision from status and policy_results, and writes termination SessionData itself.
            A successful close uses SessionData status 20 (SESSION_TERMINATION); DeviceResponse status 0 means OK.
            No private reader key or encrypted SessionData is accepted.
            NFC static handover sends handoverSelect only; negotiated handover also sends handoverRequest. Preserve both exactly.
        """.trimIndent()
        request {
            body<ExternalReaderDeviceResponse> {
                example("QR null handover") { value = qr }
                example("NFC static handover") {
                    value = qr.copy(handover = buildJsonObject {
                        put("type", "nfc")
                        put("handoverSelect", "<base64 exact handover-select bytes>")
                    })
                }
                example("NFC negotiated handover") {
                    value = qr.copy(handover = buildJsonObject {
                        put("type", "nfc")
                        put("handoverSelect", "<base64 exact handover-select bytes>")
                        put("handoverRequest", "<base64 exact handover-request bytes>")
                    })
                }
            }
        }
        response {
            HttpStatusCode.OK to { body<Verification2Session>(); description = "Stored terminal outcome (SUCCESSFUL or FAILED)" }
            HttpStatusCode.BadRequest to { description = "Malformed submission; no session created" }
            HttpStatusCode.NotFound to { description = "Proximity verifier feature is disabled" }
        }
    }
}
