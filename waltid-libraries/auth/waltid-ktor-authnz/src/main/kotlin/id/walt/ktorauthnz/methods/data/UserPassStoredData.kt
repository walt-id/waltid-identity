package id.walt.ktorauthnz.methods.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("userpass-data")
data class UserPassStoredData(
    val password: String,
) : AuthMethodStoredData
