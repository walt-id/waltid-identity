package id.walt.commons.web.modules

import io.ktor.server.application.*
import io.ktor.server.auth.*

object AuthenticationServiceModule {

    object AuthenticationServiceConfig {
        var customAuthentication: (AuthenticationConfig.() -> Unit)? = null
    }

    // Module
    fun Application.enable() {
        install(Authentication) {
            AuthenticationServiceConfig.customAuthentication?.invoke(this)
        }
    }
}
