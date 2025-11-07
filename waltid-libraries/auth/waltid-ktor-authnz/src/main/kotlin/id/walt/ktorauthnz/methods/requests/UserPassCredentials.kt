package id.walt.ktorauthnz.methods.requests

import kotlinx.serialization.Serializable

@Serializable
data class UserPassCredentials(val username: String, val password: String)
