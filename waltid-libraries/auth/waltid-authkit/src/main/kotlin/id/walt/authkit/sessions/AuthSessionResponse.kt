package id.walt.authkit.sessions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthSessionResponse(
    val session: String, // Session ID
    val status: AuthSessionStatus,

    val user: String,
    @SerialName("auth_token")
    val authToken: String
)
