package id.walt.ktorauthnz.methods.virtual

/*
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.methods.data.FlowAmendmentData
import id.walt.ktorauthnz.sessions.SessionManager
import io.github.smiley4.ktoropenapi.post
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.Serializable

object IdentifyEmail : IdentifyVirtualAuth("identify") {

    @Serializable
    data class IdentifyEmailRequest(val email: String)

    override fun Route.register(authContext: ApplicationCall.() -> AuthContext) {
        post("identify-email", {
            request { body<IdentifyEmailRequest>() }
        }) {
            // TODO: support the others (form, etc)

            val email = call.receive<IdentifyEmailRequest>().email
            val identifier = EmailIdentifier(email)

            val store = KtorAuthnzManager.accountStore

            val data = when {
                store.hasStoredDataFor(identifier, this@IdentifyEmail) ->
                    store.lookupStoredDataForAccountIdentifier(identifier, this@IdentifyEmail)

                store.hasStoredDataFor(identifier, GlobalIdentify) ->
                    store.lookupStoredDataForAccountIdentifier(identifier, GlobalIdentify)

                else -> error("No global identifier found for $email")
            } as FlowAmendmentData

            val session = getSession(authContext)

            when {
                data.appendFlow != null && session.flows != null -> session.flows = session.flows!! + data.appendFlow
                data.replaceFlow != null -> session.flows = data.replaceFlow
            }
            SessionManager.updateSession(session)
        }
    }
}*/
