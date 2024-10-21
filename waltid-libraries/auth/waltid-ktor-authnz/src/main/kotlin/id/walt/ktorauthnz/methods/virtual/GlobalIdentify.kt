package id.walt.ktorauthnz.methods.virtual

import id.walt.ktorauthnz.AuthContext
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*

object GlobalIdentify : IdentifyVirtualAuth("identify-global") {

    override fun Route.register(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {
        throw NotImplementedError("This method is internally referenced and not to be used by the caller.")
    }


}
