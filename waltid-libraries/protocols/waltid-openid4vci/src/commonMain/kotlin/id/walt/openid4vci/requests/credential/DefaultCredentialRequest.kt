package id.walt.openid4vci.requests.credential

import id.walt.openid4vci.Client
import id.walt.openid4vci.Session
import id.walt.openid4vci.prooftypes.Proofs
import id.walt.openid4vci.requests.generateRequestId
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject

data class DefaultCredentialRequest(
    override val id: String = generateRequestId(),
    override val requestedAt: Instant = Clock.System.now(),
    override val client: Client,
    override val credentialIdentifier: String?,
    override val credentialConfigurationId: String?,
    override val proofs: Proofs?,
    override val credentialResponseEncryption: JsonObject?,
    override val requestForm: Map<String, List<String>> = emptyMap(),
    override val session: Session? = null,
    override val issClaim: String? = null,
) : CredentialRequest {
    override fun withSession(session: Session?): CredentialRequest = copy(session = session)
    override fun withIssuer(issClaim: String?): CredentialRequest = copy(issClaim = issClaim)
}
