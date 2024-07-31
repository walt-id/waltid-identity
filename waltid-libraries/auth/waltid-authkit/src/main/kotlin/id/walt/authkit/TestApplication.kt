package id.walt.authkit

import id.walt.authkit.methods.AuthenticationMethod
import id.walt.authkit.methods.UserPass
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*

data class AuthContext(
    val tenant: String? = null,
)

fun Application.testApp() {
    routing {
        route("auth") {

            val contextFunction: PipelineContext<Unit, ApplicationCall>.() -> AuthContext = {
                AuthContext(
                    tenant = call.request.host()
                )
            }

            registerAuthenticationMethod(UserPass, contextFunction)
        }
    }
}

fun Route.registerAuthenticationMethod(method: AuthenticationMethod, context: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {
    method.apply {
        register(context)
    }
}

fun Route.registerAuthenticationMethods(methods: List<AuthenticationMethod>, context: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {
    methods.forEach {
        it.apply {
            register(context)
        }
    }
}
