package id.walt.webwallet.web.controllers.auth

import id.walt.commons.config.ConfigManager
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.identifiers.methods.Web3Identifier
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.methods.AuthMethodManager
import id.walt.ktorauthnz.methods.AuthenticationMethod
import id.walt.ktorauthnz.methods.Web3
import id.walt.ktorauthnz.methods.registerAuthenticationMethod
import id.walt.webwallet.config.KtorAuthnzConfig
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments.*
import id.walt.webwallet.db.models.authnz.AuthnzUsers
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

private val authConfig = ConfigManager.getConfig<KtorAuthnzConfig>()
private val flowConfig = authConfig.authFlow



private val web3Registration: suspend (any: Any) -> Unit = { any ->
    val identifier = any as? Web3Identifier ?: error("Provided argument is not web3 identifier")
    val newAuthnzUserId =  transaction { AuthnzUsers.insert{}[AuthnzUsers.id] }.toString()
    KtorAuthnzManager.accountStore.addAccountIdentifierToAccount(newAuthnzUserId, identifier)
}

private val authMethodFunctionAmendments: Map<AuthenticationMethod, Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>> =
    mapOf(
        Web3 to mapOf(Registration to web3Registration)
    )

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

                val functionAmendments = authMethodFunctionAmendments[authenticationMethod]

                registerAuthenticationMethod(authenticationMethod, contextFunction, functionAmendments)
            }
        }
    }
}
