package id.walt.openid4vci.requests.credential

import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.jose.exportPublicJwk
import id.walt.crypto2.jose.Jwk
import id.walt.openid4vci.Session
import id.walt.openid4vci.proofs.VerifiedCredentialBinding
import id.walt.openid4vci.proofs.attestation.restoreAttestedKey
import id.walt.openid4vci.requests.credential.encryption.CredentialResponseEncryptionParameters
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Trusted storage representation, never a wallet request DTO. Contains no original proof JWTs. */
@Serializable
data class AcceptedCredentialRequest(
    val request: DefaultCredentialRequest,
    val credentialIdentifier: String,
    val credentialConfigurationId: String,
    val configuration: id.walt.openid4vci.metadata.issuer.CredentialConfiguration,
    val bindings: List<AcceptedHolderBinding>,
) {
    internal suspend fun equivalentTo(other: AcceptedCredentialRequest): Boolean {
        fun DefaultCredentialRequest.issuanceParameters() = requestForm.filterKeys {
            it !in setOf("proof", "proofs", "credential_identifier", "credential_configuration_id", "credential_response_encryption")
        }
        if (credentialIdentifier != other.credentialIdentifier || credentialConfigurationId != other.credentialConfigurationId ||
            configuration != other.configuration || request.accessTokenClientId != other.request.accessTokenClientId ||
            request.client.id != other.request.client.id || request.issClaim != other.request.issClaim ||
            request.session?.subject != other.request.session?.subject ||
            request.session?.customAttributes != other.request.session?.customAttributes ||
            request.anonymousPreAuthorizedAccess != other.request.anonymousPreAuthorizedAccess ||
            request.issuanceParameters() != other.request.issuanceParameters() || bindings.size != other.bindings.size) return false
        return bindings.zip(other.bindings).all { (a, b) ->
            a.holderKid == b.holderKid && a.holderDid == b.holderDid && a.proofIndexes == b.proofIndexes &&
                Jwk.sha256Thumbprint(restoreAttestedKey(a.publicJwk).exportPublicJwk()) ==
                Jwk.sha256Thumbprint(restoreAttestedKey(b.publicJwk).exportPublicJwk())
        }
    }

    suspend fun restore(encryption: CredentialResponseEncryptionParameters? = request.credentialResponseEncryption): CredentialRequest =
        PreparedCredentialRequest(
            request.copy(credentialResponseEncryption = encryption),
            configuration,
            bindings.map { VerifiedCredentialBinding(restoreAttestedKey(it.publicJwk), it.holderKid, it.holderDid, it.proofIndexes) },
        )
}

@Serializable
data class AcceptedHolderBinding(
    val publicJwk: JsonObject,
    val holderKid: String? = null,
    val holderDid: String? = null,
    val proofIndexes: Set<Int> = emptySet(),
)

/** Only the provider or a trusted persisted acceptance can construct this proof-free request. */
internal class PreparedCredentialRequest(
    private val original: CredentialRequest,
    val acceptedConfiguration: id.walt.openid4vci.metadata.issuer.CredentialConfiguration,
    val bindings: List<VerifiedCredentialBinding>,
) : CredentialRequest by original {
    override fun withSession(session: Session?): CredentialRequest =
        PreparedCredentialRequest(original.withSession(session), acceptedConfiguration, bindings)
    override fun withIssuer(issClaim: String?): CredentialRequest =
        PreparedCredentialRequest(original.withIssuer(issClaim), acceptedConfiguration, bindings)
    override fun withAccessTokenClient(clientId: String?, anonymousPreAuthorizedAccess: Boolean): CredentialRequest =
        PreparedCredentialRequest(original.withAccessTokenClient(clientId, anonymousPreAuthorizedAccess), acceptedConfiguration, bindings)
}

internal suspend fun CredentialRequest.accept(
    credentialIdentifier: String,
    credentialConfigurationId: String,
    configuration: id.walt.openid4vci.metadata.issuer.CredentialConfiguration,
    bindings: List<VerifiedCredentialBinding>,
): AcceptedCredentialRequest = AcceptedCredentialRequest(
    request = DefaultCredentialRequest(
        id = id, requestedAt = requestedAt, client = client,
        credentialIdentifier = credentialIdentifier, credentialConfigurationId = null,
        proofs = null, credentialResponseEncryption = credentialResponseEncryption,
        requestForm = requestForm.filterKeys { it !in setOf("proofs", "proof", "credential_configuration_id", "credential_identifier") },
        session = session, issClaim = issClaim, accessTokenClientId = accessTokenClientId,
        anonymousPreAuthorizedAccess = anonymousPreAuthorizedAccess,
    ),
    credentialIdentifier = credentialIdentifier,
    credentialConfigurationId = credentialConfigurationId,
    configuration = configuration,
    bindings = bindings.map { AcceptedHolderBinding(it.holderKey.exportPublicJwkObject(), it.holderKid, it.holderDid, it.proofIndexes) },
)
