package id.walt.ktorauthnz.methods.storeddata

import id.walt.ktorauthnz.methods.TOTP
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("totp")
data class TOTPStoredData(
    val secret: String,
) : AuthMethodStoredData {
    override fun authMethod() = TOTP

    companion object {
        val EXAMPLE = TOTPStoredData("secret")
    }
}
