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


fun Application.testApp() {
    routing {
        route("auth") {

            val contextFunction: PipelineContext<Unit, ApplicationCall>.() -> AuthContext = {
                AuthContext(
                    tenant = call.request.host(),
                    sessionId = call.parameters["sessionId"] ?: error("Missing sessionId")
                )
            }

            route("flows") {

                route("flow1") {
                    @Language("JSON")
                    val flowConfig = """
                        {
                            "method": "userpass",
                            "continue": {
                              "method": "totp",
                              "ok": true
                            }
                        }
                    """.trimIndent()
                    val authFlow = AuthFlow.fromConfig(flowConfig)

                    route("{sessionId}") {
                        registerAuthenticationMethod(UserPass, contextFunction)
                        registerAuthenticationMethod(TOTP, contextFunction)
                    }

                    post("start") {
                        val session = SessionManager.openExplicitSession(authFlow)
                        context.respond(session.toInformation())
                    }
                }
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
