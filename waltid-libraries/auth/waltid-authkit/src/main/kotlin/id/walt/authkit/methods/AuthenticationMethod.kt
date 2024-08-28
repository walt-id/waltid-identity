package id.walt.authkit.methods

import id.walt.authkit.AuthContext
import id.walt.authkit.accounts.AccountStore
import id.walt.authkit.accounts.identifiers.AccountIdentifier
import id.walt.authkit.methods.data.AuthMethodStoredData
import id.walt.authkit.sessions.AuthSession
import id.walt.authkit.sessions.SessionStore
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*

abstract class AuthenticationMethod(open val id: String) {
    abstract fun Route.register(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext)

//    abstract val identifier: AccountIdentifier

    inline fun <reified V : AuthMethodStoredData> lookupStoredData(identifier: AccountIdentifier): V {
        val storedData = AccountStore.lookupStoredDataFor(identifier, this)
        return (storedData as? V) ?: error("${storedData::class.simpleName} is not requested ${V::class.simpleName}")
    }

    inline fun <reified V : AuthMethodStoredData> lookupStoredMultiData(session: AuthSession): V {
        val storedData = AccountStore.lookupStoredMultiDataForAccount(session, this)
        return (storedData as? V) ?: error("${storedData::class.simpleName} is not requested ${V::class.simpleName}")
    }

    fun PipelineContext<Unit, ApplicationCall>.getSession(authContext: PipelineContext<Unit, ApplicationCall>.() -> AuthContext): AuthSession {
        val currentContext = authContext.invoke(this)
        val session = SessionStore.resolveSessionId(currentContext.sessionId)
        return session
    }


}
