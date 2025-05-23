package id.walt.ktorauthnz.methods.virtual

import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments
import io.ktor.server.application.*
import io.ktor.server.routing.*

object GlobalIdentify : IdentifyVirtualAuth("identify-global") {

    override fun Route.registerAuthenticationRoutes(
        authContext: ApplicationCall.() -> AuthContext,
        functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>?
    ) {
        throw NotImplementedError("This method is internally referenced and not to be used by the caller.")
    }


}
