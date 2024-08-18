package id.walt

import id.walt.authkit.AuthContext
import id.walt.authkit.methods.AuthenticationMethod
import id.walt.authkit.methods.OIDC
import id.walt.authkit.methods.UserPass
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*


fun Application.testApp() {
    routing {
        route("auth") {

            val contextFunction: PipelineContext<Unit, ApplicationCall>.() -> AuthContext = {
                AuthContext(
                    tenant = call.request.host()
                )
            }

            registerAuthenticationMethod(UserPass, contextFunction)
            registerAuthenticationMethod(OIDC, contextFunction)
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
