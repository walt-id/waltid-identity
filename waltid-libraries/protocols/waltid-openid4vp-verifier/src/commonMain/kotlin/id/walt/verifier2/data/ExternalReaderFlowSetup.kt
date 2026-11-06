package id.walt.verifier2.data

import id.walt.dcql.models.CredentialFormat
import id.walt.mdoc.proximity.MdocProximityVerifier
import id.walt.mdoc.proximity.MdocSessionHandover
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.io.encoding.Base64

/** A completed reader exchange, with no server-generated request or reader private key. */
@Serializable
@SerialName("iso_18013_5_external_reader")
data class ExternalReaderFlowSetup(
    @SerialName("core_flow") override val core: GeneralFlowConfig,
) : VerificationSessionSetup {
    init {
        core.requestedElements?.let(::buildRequestedElementsDcqlQuery)
        require(core.dcqlQuery?.credentials.orEmpty().all { it.format == CredentialFormat.MSO_MDOC }) {
            "External-reader queries must use mso_mdoc"
        }
        require(core.policies.vp_policies?.let { it.jwtVcJson.isEmpty() && it.dcSdJwt.isEmpty() } != false) {
            "External-reader presentation policies must use mso_mdoc"
        }
        require(core.sessionId == null) { "External-reader sessions always receive a new session id" }
        require(!core.signedRequest && !core.encryptedResponse && core.key == null) {
            "Reader request signing and session encryption belong to the external reader"
        }
    }
}

/** Presentation bytes are submission-only and are never embedded in the stored session setup. */
@Serializable
data class ExternalReaderDeviceResponse(
    @SerialName("flow_type") val flowType: String,
    @SerialName("core_flow") val core: GeneralFlowConfig,
    val deviceResponse: String,
    val deviceEngagement: String,
    val eReaderKey: String,
    /** The string "qr", or {"type":"nfc","handoverSelect":"...","handoverRequest":"..."}. */
    val handover: JsonElement,
) {
    init {
        require(flowType == "iso_18013_5_external_reader") { "Expected iso_18013_5_external_reader flow_type" }
    }

    fun parse(): MdocProximityVerifier.Submission {
        // Validate setup before spending time on cryptography or storing a session.
        ExternalReaderFlowSetup(core)
        val parsedHandover = if (handover == JsonPrimitive("qr")) MdocSessionHandover.Qr else {
            val nfc = Json.decodeFromJsonElement<NfcHandoverInput>(handover)
            require(nfc.type == "nfc") { "Expected qr or nfc handover" }
            MdocSessionHandover.NfcConnection(
                ByteString(Base64.decode(nfc.handoverSelect)),
                ByteString(Base64.decode(nfc.handoverRequest)),
            )
        }
        return MdocProximityVerifier.parse(
            Base64.decode(deviceResponse), Base64.decode(deviceEngagement), Base64.decode(eReaderKey), parsedHandover,
        )
    }
}

@Serializable
private data class NfcHandoverInput(val type: String, val handoverSelect: String, val handoverRequest: String)
