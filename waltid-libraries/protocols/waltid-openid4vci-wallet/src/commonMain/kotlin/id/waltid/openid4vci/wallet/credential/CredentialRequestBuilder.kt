package id.waltid.openid4vci.wallet.credential

import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.requests.authorization.AuthorizationDetail
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** One dataset/configuration addressed by one Credential Request, regardless of instance count. */
@Serializable
data class CredentialIssuanceTarget(
    val credentialConfigurationId: String,
    val credentialIdentifier: String? = null,
)

/** Protocol operations shared by stateless wallet handlers and retained mobile sessions. */
object CredentialRequestBuilder {
    /** Prefer advertised authorization_details support; otherwise use the selected configurations' scopes. */
    fun authorizationScope(
        metadata: CredentialIssuerMetadata,
        configurationIds: List<String>,
        authorizationServerMetadata: AuthorizationServerMetadata,
    ): String? {
        require(configurationIds.isNotEmpty()) { "At least one credential configuration must be selected" }
        val configurations = configurationIds.distinct().map {
            requireNotNull(metadata.credentialConfigurationsSupported[it]) { "Unknown credential configuration '$it'" }
        }
        if ("openid_credential" in authorizationServerMetadata.authorizationDetailsTypesSupported.orEmpty()) return null
        return configurations.map { configuration ->
            requireNotNull(configuration.scope?.takeIf(String::isNotBlank)) {
                "Issuer must advertise openid_credential authorization details or scopes for every selected credential"
            }
        }.distinct().joinToString(" ")
    }

    /** Narrow an already-authorized offer when supported; the pre-authorized code needs no extra selector. */
    fun preAuthorizedTokenParameters(
        metadata: CredentialIssuerMetadata,
        configurationIds: List<String>,
        authorizationServerMetadata: AuthorizationServerMetadata,
    ): Map<String, String> {
        require(configurationIds.isNotEmpty()) { "At least one credential configuration must be selected" }
        val configurations = configurationIds.distinct().map {
            requireNotNull(metadata.credentialConfigurationsSupported[it]) { "Unknown credential configuration '$it'" }
        }
        if ("openid_credential" !in authorizationServerMetadata.authorizationDetailsTypesSupported.orEmpty()) {
            // Partial scope lists could accidentally narrow away an accepted configuration.
            val scopes = configurations.map { it.scope?.takeIf(String::isNotBlank) ?: return emptyMap() }
            return mapOf("scope" to scopes.distinct().joinToString(" "))
        }
        val details = buildJsonArray {
            configurationIds.distinct().forEach { configurationId ->
                add(buildJsonObject {
                    put("type", "openid_credential")
                    put("credential_configuration_id", configurationId)
                    if (!metadata.authorizationServers.isNullOrEmpty()) {
                        put("locations", buildJsonArray { add(metadata.credentialIssuer) })
                    }
                })
            }
        }
        return mapOf("authorization_details" to details.toString())
    }

    fun build(target: CredentialIssuanceTarget, jwtProofs: List<String>? = null): JsonObject = buildJsonObject {
        require(target.credentialConfigurationId.isNotBlank()) { "Credential configuration must not be blank" }
        if (target.credentialIdentifier != null) {
            require(target.credentialIdentifier.isNotBlank()) { "Credential identifier must not be blank" }
            put("credential_identifier", target.credentialIdentifier)
        } else put("credential_configuration_id", target.credentialConfigurationId)
        jwtProofs?.let {
            require(it.isNotEmpty() && it.none(String::isBlank)) { "Supply a non-empty collection of JWT proofs" }
            putJsonObject("proofs") { put("jwt", JsonArray(it.map(::JsonPrimitive))) }
        }
    }

    fun validateBatchSize(metadata: CredentialIssuerMetadata, count: Int) {
        require(count >= 1) { "At least one holder binding is required" }
        if (count == 1) return
        val limit = requireNotNull(metadata.batchCredentialIssuance) { "Issuer does not advertise batch issuance" }.batchSize
        require(count <= limit) { "Requested batch size $count exceeds issuer limit $limit" }
    }

    /** Only selected and granted targets are returned; token identifiers are never discarded. */
    fun resolveTargets(
        metadata: CredentialIssuerMetadata,
        configurationIds: List<String>,
        authorizationDetails: List<AuthorizationDetail>?,
        scope: String? = null,
    ): List<CredentialIssuanceTarget> {
        require(configurationIds.isNotEmpty()) { "At least one credential configuration must be selected" }
        require(configurationIds.distinct().size == configurationIds.size) { "Credential configurations must be selected once" }
        configurationIds.forEach { require(it in metadata.credentialConfigurationsSupported) { "Unknown credential configuration '$it'" } }
        val details = authorizationDetails?.filter { it.type == "openid_credential" }
        val targets = if (!details.isNullOrEmpty()) {
            configurationIds.flatMap { configurationId ->
                details.filter {
                    it.credentialConfigurationId == configurationId &&
                        it.locations?.contains(metadata.credentialIssuer) != false
                }.flatMap { detail ->
                    val identifiers = requireNotNull(detail.credentialIdentifiers) { "Token authorization details contain no credential identifiers" }
                    require(identifiers.isNotEmpty() && identifiers.none(String::isBlank)) { "Token credential identifiers must not be empty" }
                    identifiers.map { CredentialIssuanceTarget(configurationId, it) }
                }
            }.distinct()
        } else {
            val scopes = scope?.split(' ')?.filter(String::isNotBlank)?.toSet()
            configurationIds.filter { scopes == null || metadata.credentialConfigurationsSupported.getValue(it).scope in scopes }
                .map { CredentialIssuanceTarget(it) }
        }
        require(targets.isNotEmpty()) { "Token grants none of the selected credentials" }
        return targets
    }
}
