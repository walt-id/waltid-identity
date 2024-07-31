package id.walt.authkit.methods

import id.walt.authkit.AuthContext
import id.walt.authkit.accounts.AccountStore
import id.walt.authkit.accounts.identifiers.AccountIdentifier
import id.walt.authkit.methods.data.AuthMethodStoredData
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*

abstract class AuthenticationMethod {
    abstract fun Route.register(context: PipelineContext<Unit, ApplicationCall>.() -> AuthContext)

//    abstract val identifier: AccountIdentifier

    inline fun <reified V: AuthMethodStoredData> lookupStoredData(identifier: AccountIdentifier): V {
        val storedData = AccountStore.lookupStoredDataFor(identifier, this)
        return (storedData as? V) ?: error("${storedData::class.simpleName} is not requested ${V::class.simpleName}")
    }



}
