package id.walt.authkit.methods.virtual

import id.walt.authkit.AuthContext
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*

object IdentifyEmail : VirtualAuthMethod("identify") {
    override fun Route.register(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {
        post("identify-email", {

        }) {

        }
    }
}
