package id.walt.ktorauthnz.methods.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("totp-data")
data class TOTPStoredData(
    val secret: String,
) : AuthMethodStoredData
