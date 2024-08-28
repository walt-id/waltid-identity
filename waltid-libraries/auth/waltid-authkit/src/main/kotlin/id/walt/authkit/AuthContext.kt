package id.walt.authkit

data class AuthContext(
    val tenant: String? = null,
    val sessionId: String,
)
