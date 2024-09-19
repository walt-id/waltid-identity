package id.walt

import id.walt.authkit.AuthContext
import id.walt.authkit.auth.authKit
import id.walt.authkit.auth.getAuthToken
import id.walt.authkit.auth.getAuthenticatedAccount
import id.walt.authkit.flows.AuthFlow
import id.walt.authkit.methods.*
import id.walt.authkit.sessions.SessionManager
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import org.intellij.lang.annotations.Language

fun Route.globalImplicitSingleStep() {
    route("global-implicit1") {
        @Language("JSON")
        val flowConfig = """
            {
                "method": "userpass",
                "ok": true
            }
        """.trimIndent()
        val authFlow = AuthFlow.fromConfig(flowConfig)


        val contextFunction: PipelineContext<Unit, ApplicationCall>.() -> AuthContext = {
            AuthContext(
                tenant = call.request.host(),
                sessionId = call.parameters["sessionId"],
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
                  "ok": true
                }]
            }
        """.trimIndent()
        val authFlow = AuthFlow.fromConfig(flowConfig)


        val contextFunction: PipelineContext<Unit, ApplicationCall>.() -> AuthContext = {
            AuthContext(
                tenant = call.request.host(),
                sessionId = call.parameters["sessionId"],
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

        val contextFunction: PipelineContext<Unit, ApplicationCall>.() -> AuthContext = {
            AuthContext(
                tenant = call.request.host(),
                sessionId = call.parameters["sessionId"] ?: error("Missing sessionId")
            )
        }

        @Language("JSON")
        val flowConfig = """
            {
                "method": "userpass",
                "continue": [{
                  "method": "totp",
                  "ok": true
                }]
            }
        """.trimIndent()
        val authFlow = AuthFlow.fromConfig(flowConfig)

        route("{sessionId}") {
            registerAuthenticationMethods(methods, contextFunction)
        }

        post("start") {
            val session = SessionManager.openExplicitGlobalSession(authFlow)
            context.respond(session.toInformation())
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
            "ok": true
        }
    """.trimIndent()
        val authFlow = AuthFlow.fromConfig(flowConfig)


        val contextFunction: PipelineContext<Unit, ApplicationCall>.() -> AuthContext = {
            AuthContext(
                tenant = call.request.host(),
                sessionId = call.parameters["sessionId"],
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
                "ok": true
            }
        """.trimIndent()
        val authFlow = AuthFlow.fromConfig(flowConfig)


        val contextFunction: PipelineContext<Unit, ApplicationCall>.() -> AuthContext = {
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
            context.respond(session.toInformation())
        }
    }
}*/



fun Route.authFlowRoutes() {
    // Global flows (service specifies flow)
    globalImplicitSingleStep()
    globalImplicitMultiStep()
    globalExplicitMultiStep()

    globalImplicitVc()

    // Account flows (account specifies flow)
    //accountImplicitMultiStep()
}

fun Application.testApp() {
    install(Authentication) {
        authKit("authkit") {

        }
    }


    routing {
        route("auth") {
            route("flows") {
                authFlowRoutes()
            }
        }

        authenticate("authkit") {
            get("/protected") {
                val token = getAuthToken()
                val accountId = getAuthenticatedAccount()
                call.respondText("Hello token ${token}, you are $accountId")
            }
        }

    }
}


