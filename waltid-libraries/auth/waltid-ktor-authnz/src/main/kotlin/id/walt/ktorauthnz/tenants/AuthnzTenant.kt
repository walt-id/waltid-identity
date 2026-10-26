package id.walt.ktorauthnz.tenants

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** The tenant an authentication call works for, in the coroutine context; see [authnzTenant]. */
class AuthnzTenant(val id: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AuthnzTenant>

    override fun toString() = "AuthnzTenant($id)"
}

/**
 * The tenant the current call works for, or null outside a tenant scope.
 *
 * Account stores of multi-tenant applications read it to keep each tenant's accounts apart: the store interfaces take
 * no tenant parameter, as single-tenant applications have none.
 */
suspend fun currentAuthnzTenant(): String? = currentCoroutineContext()[AuthnzTenant]?.id

/** Runs [block] for [tenant] - e.g. to set up a tenant's accounts outside of a request. */
suspend fun <T> inAuthnzTenant(tenant: String, block: suspend () -> T): T = withContext(AuthnzTenant(tenant)) { block() }

private val tenantAttribute = AttributeKey<String>("ktor-authnz-tenant")

/** The tenant of this call, if it is under an [authnzTenant] scope. */
val ApplicationCall.authnzTenant: String? get() = attributes.getOrNull(tenantAttribute)

/**
 * Scopes every route below this one to the tenant [resolve] names for a call - from the path, the host, a header,
 * whatever the application uses:
 *
 * ```kotlin
 * route("{tenant}") {
 *     authnzTenant { parameters["tenant"]!! }
 *     route("auth") { authFlows(...) }
 *     authenticate("authnz") { ... }
 * }
 * ```
 *
 * Below it, sessions are opened for and bound to the tenant, account store calls see it as [currentAuthnzTenant],
 * attempt limits and password reset tokens are kept per tenant, and the ktor-authnz provider refuses tokens issued for
 * another tenant (or for none).
 */
fun Route.authnzTenant(resolve: ApplicationCall.() -> String) {
    intercept(ApplicationCallPipeline.Plugins) {
        val tenant = call.resolve()
        call.attributes.put(tenantAttribute, tenant)
        withContext(AuthnzTenant(tenant)) { proceed() }
    }
}
