@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.web.controllers.auth

import id.walt.commons.config.ConfigManager
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.identifiers.methods.Web3Identifier
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments.Registration
import id.walt.ktorauthnz.methods.AuthMethodManager
import id.walt.ktorauthnz.methods.AuthenticationMethod
import id.walt.ktorauthnz.methods.Web3
import id.walt.ktorauthnz.methods.registerAuthenticationMethod
import id.walt.webwallet.config.KtorAuthnzConfig
import id.walt.webwallet.db.models.Accounts
import id.walt.webwallet.db.models.authnz.AuthnzUsers
import id.walt.webwallet.service.account.AccountsService.initializeUserAccount
import io.github.smiley4.ktoropenapi.route
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val authConfig = ConfigManager.getConfig<KtorAuthnzConfig>()
private val flowConfig = authConfig.authFlow


private val web3Registration: suspend (any: Any) -> Unit = { any ->
    val identifier = any as? Web3Identifier ?: error("Provided argument is not web3 identifier")
    val web3Address = identifier.address

    val newAuthnzUserId = transaction { AuthnzUsers.insert {}[AuthnzUsers.id] }.toString()
    val newAuthnzUserUuid = Uuid.parse(newAuthnzUserId)


    KtorAuthnzManager.accountStore.addAccountIdentifierToAccount(newAuthnzUserId, identifier)

    transaction {
        val accountId = Accounts.insert {
            it[tenant] = ""
            it[id] = newAuthnzUserUuid
            it[name] = web3Address
            it[createdOn] = Clock.System.now().toJavaInstant()
        }[Accounts.id]
    }

    initializeUserAccount(name = web3Address, registeredUserId = Uuid.parse(newAuthnzUserId))
}

private val authMethodFunctionAmendments: Map<AuthenticationMethod, Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>> =
    mapOf(
        Web3 to mapOf(Registration to web3Registration)
    )

fun Application.ktorAuthnzRoutes() {
    routing {
        route("wallet-api/auth", {
            tags("Authentication")
        }) {
            route("account", {
                summary = "Account authentication"
                description = "Configured authentication flow:<br/><br/>${flowConfig.toString().replace("\n", "<br/>")}"
            }) {
                val contextFunction: ApplicationCall.() -> AuthContext = {
                    AuthContext(
                        tenant = request.host(),
                        sessionId = parameters["sessionId"],
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
