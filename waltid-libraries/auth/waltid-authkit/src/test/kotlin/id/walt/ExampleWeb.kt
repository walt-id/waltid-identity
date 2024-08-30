package id.walt

import id.walt.authkit.AuthContext
import id.walt.authkit.flows.AuthFlow
import id.walt.authkit.methods.AuthenticationMethod
import id.walt.authkit.methods.TOTP
import id.walt.authkit.methods.UserPass
import id.walt.authkit.sessions.SessionManager
import io.ktor.server.application.*
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
    route("flow-global2") {
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
            val session = SessionManager.openExplicitSession(authFlow)
            context.respond(session.toInformation())
        }
    }
}

fun Route.flowRoutes() {
    // Global flows (service specifies flow)
    globalImplicitSingleStep()
    globalImplicitMultiStep()
    globalExplicitMultiStep()

    // Account flows (account specifies flow)

}

fun Application.testApp() {
    routing {
        route("auth") {

            route("flows") {
                flowRoutes()
            }
        }
    }
}

fun Route.registerAuthenticationMethod(
    method: AuthenticationMethod,
    authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext,

    ) {
    method.apply {
        register(authContext)
    }
}

fun Route.registerAuthenticationMethods(
    methods: List<AuthenticationMethod>,
    authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext,
) {
    methods.forEach {
        it.apply {
            register(authContext)
        }
    }
}
