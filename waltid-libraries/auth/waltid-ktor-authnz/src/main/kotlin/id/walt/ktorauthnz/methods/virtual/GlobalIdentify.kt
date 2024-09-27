package id.walt.ktorauthnz.methods.virtual

import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.methods.data.AuthMethodStoredData
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
