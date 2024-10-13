package id.walt.ktorauthnz.methods.config

import id.walt.ktorauthnz.methods.AuthenticationMethod
import kotlinx.serialization.Serializable

@Serializable
sealed interface AuthMethodConfiguration {
    fun authMethod(): AuthenticationMethod
}
