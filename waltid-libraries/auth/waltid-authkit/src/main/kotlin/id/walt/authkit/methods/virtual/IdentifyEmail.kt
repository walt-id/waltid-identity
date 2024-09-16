package id.walt.authkit.methods.virtual

import id.walt.authkit.AuthContext
import id.walt.authkit.AuthKitManager
import id.walt.authkit.accounts.identifiers.EmailIdentifier
import id.walt.authkit.sessions.SessionManager
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable

object IdentifyEmail : IdentifyVirtualAuth("identify") {

    @Serializable
    data class IdentifyEmailRequest(val email: String)

    override fun Route.register(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext) {
        post("identify-email", {
            request { body<IdentifyEmailRequest>() }
        }) {
            // TODO: support the others (form, etc)

            val email = context.receive<IdentifyEmailRequest>().email
            val identifier = EmailIdentifier(email)

            val store = AuthKitManager.accountStore

            val data = when {
                store.hasStoredDataFor(identifier, this@IdentifyEmail) ->
                    store.lookupStoredDataFor(identifier, this@IdentifyEmail)

                store.hasStoredDataFor(identifier, GlobalIdentify) ->
                    store.lookupStoredDataFor(identifier, GlobalIdentify)

                else -> error("No global identifier found for $email")
            } as GlobalIdentify.FlowAmendmentData

            val session = getSession(authContext)

            when {
                data.appendFlow != null && session.flows != null -> session.flows = session.flows!! + data.appendFlow
                data.replaceFlow != null -> session.flows = data.replaceFlow
            }
            SessionManager.updateSession(session)
        }
    }
}