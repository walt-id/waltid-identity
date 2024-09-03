package id.walt.authkit.methods.virtual

import id.walt.authkit.AuthContext
import id.walt.authkit.flows.AuthFlow
import id.walt.authkit.methods.data.AuthMethodStoredData
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable

object GlobalIdentify : IdentifyVirtualAuth("identify-global") {

    override fun Route.register(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {
        throw NotImplementedError("This method is internally referenced and not to be used by the caller.")
    }

    @Serializable
    data class FlowAmendmentData(
        val appendFlow: Set<AuthFlow>? = null,
        var replaceFlow: Set<AuthFlow>? = null,
    ): AuthMethodStoredData
}
