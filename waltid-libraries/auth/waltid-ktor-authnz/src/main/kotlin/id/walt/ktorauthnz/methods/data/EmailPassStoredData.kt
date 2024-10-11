package id.walt.ktorauthnz.methods.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("emailpass-data")
data class EmailPassStoredData(
    val password: String,
) : AuthMethodStoredData
