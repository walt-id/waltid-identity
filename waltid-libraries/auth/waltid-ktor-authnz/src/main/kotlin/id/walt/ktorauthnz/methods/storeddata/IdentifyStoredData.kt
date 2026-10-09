package id.walt.ktorauthnz.methods.storeddata

import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.methods.Identify
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The ways an account can log in, offered after the `identify` step: e.g. a password then TOTP, or an email code. The
 * application keeps it up to date as the user adds or removes methods. Method settings an account does not carry
 * (the OIDC provider, the LDAP server, ...) come from the `identify` step's [id.walt.ktorauthnz.methods.config.IdentifyConfiguration.methods].
 */
@Serializable
@SerialName("identify")
data class IdentifyStoredData(
    val flows: Set<AuthFlow>,
) : AuthMethodStoredData {
    override fun authMethod() = Identify
}
