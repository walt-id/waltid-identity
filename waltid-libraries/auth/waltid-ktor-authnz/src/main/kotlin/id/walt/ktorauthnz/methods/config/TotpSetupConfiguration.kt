package id.walt.ktorauthnz.methods.config

import id.walt.ktorauthnz.methods.TotpSetup
import kotlinx.serialization.Serializable

/** Settings of the `totp-setup` step: [issuer] is the service name authenticator apps show. */
@Serializable
data class TotpSetupConfiguration(
    val issuer: String = "ktor-authnz",
) : AuthMethodConfiguration {
    override fun authMethod() = TotpSetup
}
