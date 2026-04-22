package id.walt.openid4vci.requests.credential

import id.walt.openid4vci.Client
import id.walt.openid4vci.Session
import id.walt.openid4vci.prooftypes.Proofs
import id.walt.openid4vci.requests.generateRequestId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject

@Serializable
data class DefaultCredentialRequest(
    @SerialName("id")
    override val id: String = generateRequestId(),
    @SerialName("requested_at")
    override val requestedAt: Instant = Clock.System.now(),
    @SerialName("client")
    override val client: Client,
    @SerialName("credential_identifier")
    override val credentialIdentifier: String?,
    @SerialName("credential_configuration_id")
    override val credentialConfigurationId: String?,
    @SerialName("proofs")
    override val proofs: Proofs?,
    @SerialName("credential_response_encryption")
    override val credentialResponseEncryption: JsonObject?,
    @SerialName("request_form")
    override val requestForm: Map<String, List<String>> = emptyMap(),
    @SerialName("session")
    override val session: Session? = null,
    @SerialName("iss_claim")
    override val issClaim: String? = null,
) : CredentialRequest {
    override fun withSession(session: Session?): CredentialRequest = copy(session = session)
    override fun withIssuer(issClaim: String?): CredentialRequest = copy(issClaim = issClaim)
}
