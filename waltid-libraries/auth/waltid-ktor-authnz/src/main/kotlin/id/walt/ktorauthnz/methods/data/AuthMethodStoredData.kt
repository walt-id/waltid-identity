package id.walt.ktorauthnz.methods.data

import id.walt.ktorauthnz.methods.AuthenticationMethod
import kotlinx.serialization.Serializable

@Serializable
sealed interface AuthMethodStoredData {
    fun authMethod(): AuthenticationMethod
}
