package id.walt.webwallet.web.controllers.auth

import id.walt.commons.config.ConfigManager
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.methods.AuthMethodManager
import id.walt.ktorauthnz.methods.AuthenticationMethod
import id.walt.ktorauthnz.methods.registerAuthenticationMethod
import id.walt.webwallet.config.KtorAuthnzConfig
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*

private val authConfig = ConfigManager.getConfig<KtorAuthnzConfig>()
private val flowConfig = authConfig.authFlow

fun Application.ktorAuthnzRoutes() {
    routing {
        route("auth", {
            tags("Authentication")
        }) {
            route("account", {
                summary = "Account authentication"
                description = "Configured authentication flow:<br/><br/>${flowConfig.toString().replace("\n", "<br/>")}"
            }) {
                val contextFunction: PipelineContext<Unit, ApplicationCall>.() -> AuthContext = {
                    AuthContext(
                        tenant = call.request.host(),
                        sessionId = call.parameters["sessionId"],
                        implicitSessionGeneration = true,
                        initialFlow = authConfig.authFlow
                    )
                }

                val methodId: String = flowConfig.method
                val authenticationMethod: AuthenticationMethod = AuthMethodManager.getAuthenticationMethodById(methodId)

                registerAuthenticationMethod(authenticationMethod, contextFunction)
            }
        }
    }
}
