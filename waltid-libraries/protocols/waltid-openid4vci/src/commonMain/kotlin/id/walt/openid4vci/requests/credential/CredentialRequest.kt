package id.walt.openid4vci.requests.credential

import id.walt.openid4vci.Client
import id.walt.openid4vci.Session
import id.walt.openid4vci.prooftypes.Proofs
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject

/**
 * Credential request for the credential endpoint.
 *
 * The issuer application is responsible for resolving offers/templates and populating:
 * - credentialIdentifier/credentialConfigurationId: mutually exclusive identifiers for issuance
 * - proofs: proof object from the wallet (only JWT proofs are handled for now)
 * - session: subject and any issuance context
 * - issClaim: issuer identifier (DID/base URL) to use for signing
 * - credential configuration is supplied when calling createCredentialResponse for now
 */
interface CredentialRequest {
    val id: String
    val requestedAt: Instant
    val client: Client
    val credentialIdentifier: String?
    val credentialConfigurationId: String?
    val proofs: Proofs?
    val credentialResponseEncryption: JsonObject?
    val requestForm: Map<String, List<String>>
    val session: Session?
    val issClaim: String?

    fun withSession(session: Session?): CredentialRequest
    fun withIssuer(issClaim: String?): CredentialRequest
}
