package id.walt.ktorauthnz.methods.config

import id.walt.ktorauthnz.methods.RADIUS
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("radius-config")
data class RADIUSConfiguration(
    val radiusServerHost: String,
    val radiusServerPort: Int,
    val radiusServerSecret: String,
    val radiusNasIdentifier: String,
) : AuthMethodConfiguration {
    override fun authMethod() = RADIUS
}
