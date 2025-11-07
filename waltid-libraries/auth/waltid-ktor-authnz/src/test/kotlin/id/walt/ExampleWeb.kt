package id.walt

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.ExampleAccountStore
import id.walt.ktorauthnz.auth.*
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.methods.*
import id.walt.ktorauthnz.sessions.SessionManager
import id.walt.ktorauthnz.tokens.jwttoken.JwtTokenHandler
import id.walt.ktorauthnz.tokens.ktorauthnztoken.KtorAuthNzTokenHandler
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language

fun Route.globalImplicitOidcExample() {
    @Language("JSON")
    val flowConfig = """
{
  "method": "oidc",
  "config": {
    "openIdConfigurationUrl": "http://localhost:8080/realms/master/.well-known/openid-configuration",
    "clientId": "waltid_ktor_authnz",
    "clientSecret": "fzYFC6oAgbjozv8NoaXuOIfPxmT4XoVM",
    "callbackUri": "http://authnz-example.localhost:8088/auth/flows/oidc-example/oidc/callback",
    "pkceEnabled": true,
    "redirectAfterLogin": "http://authnz-example.localhost:8088/protected"
  },
  "success": true
}
    """.trimIndent()

    route("oidc-example") {
        val authFlow = AuthFlow.fromConfig(flowConfig)

        val contextFunction: ApplicationCall.() -> AuthContext = {
            AuthContext(
                tenant = request.host(),
                sessionId = parameters["sessionId"],
                implicitSessionGeneration = true,
                initialFlow = authFlow,
                revealTokenToClient = false
            )
        }

        registerAuthenticationMethod(OIDC, contextFunction)
    }
}

fun Route.globalMultistepExample() {
    route("web3") {
        @Language("JSON")
        val flowConfig = """
        {
            "method": "web3",
            "success": true
        }
    """.trimIndent()
        val authFlow = AuthFlow.fromConfig(flowConfig)

        val contextFunction: ApplicationCall.() -> AuthContext = {
            AuthContext(
                tenant = request.host(),
                sessionId = parameters["sessionId"],
                implicitSessionGeneration = true,
                initialFlow = authFlow
            )
        }

        registerAuthenticationMethod(Web3, contextFunction)
    }
}

fun Route.globalImplicitSingleStep() {
    route("global-implicit1") {
        @Language("JSON")
        val flowConfig = """
            {
                "method": "userpass",
                "success": true,
                "expiration": "1d"
            }
        """.trimIndent()
        val authFlow = AuthFlow.fromConfig(flowConfig)


        val contextFunction: ApplicationCall.() -> AuthContext = {
            AuthContext(
                tenant = request.host(),
                sessionId = parameters["sessionId"],
                implicitSessionGeneration = true,
                initialFlow = authFlow
            )
        }

        registerAuthenticationMethod(UserPass, contextFunction)
    }
}

fun Route.globalImplicitMultiStep() {
    route("global-implicit2") {
        @Language("JSON")
        val flowConfig = """
            {
                "method": "userpass",
                "continue": [{
                  "method": "totp",
                  "success": true
                }]
            }
        """.trimIndent()
        val authFlow = AuthFlow.fromConfig(flowConfig)


        val contextFunction: ApplicationCall.() -> AuthContext = {
            AuthContext(
                tenant = request.host(),
                sessionId = parameters["sessionId"],
                implicitSessionGeneration = true,
                initialFlow = authFlow
            )
        }

        registerAuthenticationMethod(UserPass, contextFunction)
        route("{sessionId}") {
            registerAuthenticationMethod(TOTP, contextFunction)
        }
    }
}

fun Route.globalExplicitMultiStep() {
    route("global-explicit2") {
        val methods = listOf(UserPass, TOTP)

        val contextFunction: ApplicationCall.() -> AuthContext = {
            AuthContext(
                tenant = request.host(),
                sessionId = parameters["sessionId"] ?: error("Missing sessionId")
            )
        }

        @Language("JSON")
        val flowConfig = """
            {
                "method": "userpass",
                "continue": [{
                  "method": "totp",
                  "success": true
                }]
            }
        """.trimIndent()
        val authFlow = AuthFlow.fromConfig(flowConfig)

        route("{sessionId}") {
            registerAuthenticationMethods(methods, contextFunction)
        }

        post("start") {
            val session = SessionManager.openExplicitGlobalSession(authFlow)
            call.respond(session.toInformation())
        }
    }
}

fun Route.globalImplicitVc() {
    route("global-implicit-vc") {
        @Language("JSON")
        val flowConfig = """
        {
            "method": "vc",
            "config": {
                "verification": {
                    "request_credentials": [
                        "OpenBadgeCredential"
                    ]
                }
            },
            "success": true
        }
    """.trimIndent()
        val authFlow = AuthFlow.fromConfig(flowConfig)


        val contextFunction: ApplicationCall.() -> AuthContext = {
            AuthContext(
                tenant = request.host(),
                sessionId = parameters["sessionId"],
                implicitSessionGeneration = true,
                initialFlow = authFlow
            )
        }

        registerAuthenticationMethod(VerifiableCredential, contextFunction)
    }


}

/*fun Route.accountImplicitMultiStep() {
    route("flow-account1") {

        *//*@Language("JSON")
        val flowConfig = """
            {
                "method": "userpass",
                "success": true
            }
        """.trimIndent()
        val authFlow = AuthFlow.fromConfig(flowConfig)


        val contextFunction: ApplicationCall.() -> AuthContext = {
            AuthContext(
                tenant = call.request.host(),
                sessionId = call.parameters["sessionId"],
                implicitSessionGeneration = true,
                initialFlow = authFlow
            )
        }*//*


        registerAuthenticationMethod(UserPass, contextFunction)
        route("{sessionId}") {
            registerAuthenticationMethod(TOTP, contextFunction)
        }


        post("start") {
            val session = SessionManager.openExplicitGlobalSession(authFlow)
            call.respond(session.toInformation())
        }
    }
}*/



fun Route.authFlowRoutes() {
    // Global flows (service specifies flow)
    globalImplicitSingleStep()
    globalImplicitMultiStep()
    globalExplicitMultiStep()

    globalImplicitVc()
    globalImplicitOidcExample()

    // Account flows (account specifies flow)
    //accountImplicitMultiStep()
}

@OptIn(ExternallyProvidedJWTCannotResolveToAuthenticatedSession::class)
fun Application.testApp(jwt: Boolean) {
    install(Authentication) {
        KtorAuthnzManager.tokenHandler = when {
            jwt -> JwtTokenHandler().apply {
                signingKey = runBlocking { JWKKey.generate(KeyType.Ed25519) }
                verificationKey = signingKey
            }
            else -> KtorAuthNzTokenHandler()
        }
        KtorAuthnzManager.accountStore = ExampleAccountStore

        ktorAuthnz("ktor-authnz") {
        }
    }


    routing {
        route("auth") {
            route("flows") {
                authFlowRoutes()
            }
        }

        authenticate("ktor-authnz") {
            get("/protected") {
                val token = call.getAuthToken()
                val accountId = runCatching { call.getAuthenticatedAccount() }
                val session = runCatching { getAuthenticatedSession() }
                call.respondText("Hello token ${token}, you are $accountId; session details: $session")
            }

            get("/logout") {
                val session = getAuthenticatedSession()
                session.run {
                    call.logoutAndDeleteCookie()
                }
                call.respond("Logged out")
            }
        }

    }
}


