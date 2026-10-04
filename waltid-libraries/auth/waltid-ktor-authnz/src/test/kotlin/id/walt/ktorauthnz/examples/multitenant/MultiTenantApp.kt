package id.walt.ktorauthnz.examples.multitenant

import id.walt.errors.HttpStatusError
import id.walt.ktorauthnz.KtorAuthnz
import id.walt.ktorauthnz.accounts.EditableAccountStore
import id.walt.ktorauthnz.auth.authnzPrincipal
import id.walt.ktorauthnz.auth.ktorAuthnz
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.flows.authFlows
import id.walt.ktorauthnz.methods.*
import id.walt.ktorauthnz.methods.config.EmailCodeDelivery
import id.walt.ktorauthnz.methods.config.EmailCodeSettings
import id.walt.ktorauthnz.tenants.authnzTenant
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/*
 * Example: a multi-tenant application where every tenant configures its own login.
 *
 *   org1: username + password, then TOTP, then a code sent by email
 *   org2: OIDC with the tenant's identity provider
 *   org3: LDAP (username + password against the tenant's directory), then TOTP
 *   org4: presenting a verifiable credential from a wallet
 *
 * Tenants are the first path segment (`/org1/auth/userpass`, `/org1/me`); a host or header works the same way.
 * Everything below `authnzTenant` is scoped to the tenant: sessions, accounts (the store sees `currentAuthnzTenant()`),
 * attempt limits, and tokens - a token of org1 is not accepted on org2's routes.
 *
 * MultiTenantAppTest runs this example against a mock IdP, a mock verifier and an in-memory LDAP server.
 */

/** Where each tenant's login flow comes from: here a map, in an application its database or configuration. */
class TenantDirectory(private val flows: Map<String, AuthFlow>) {
    fun flowsOf(tenant: String): List<AuthFlow> = listOf(flows[tenant] ?: throw NotFoundException("Unknown tenant: $tenant"))

    companion object {
        /** The flows of the four example tenants, as an operator would configure them (JSON, e.g. from a database). */
        fun example(oidcDiscoveryUrl: String, ldapUrl: String, verifierUrl: String) = TenantDirectory(
            mapOf(
                "org1" to AuthFlow.fromConfig(
                    """{"method": "userpass", "continue": [
                         {"method": "totp", "continue": [
                           {"method": "email-code", "success": true, "expiration": "8h"}]}]}"""
                ),
                "org2" to AuthFlow.fromConfig(
                    """{"method": "oidc", "success": true, "config": {
                         "openIdConfigurationUrl": "$oidcDiscoveryUrl",
                         "clientId": "org2-app", "clientSecret": "org2-secret",
                         "callbackUri": "http://localhost/org2/auth/oidc/callback"}}"""
                ),
                "org3" to AuthFlow.fromConfig(
                    """{"method": "ldap", "config": {"ldapServerUrl": "$ldapUrl", "userDNFormat": "uid=%s,ou=people,dc=org3"},
                        "continue": [{"method": "totp", "success": true}]}"""
                ),
                "org4" to AuthFlow.fromConfig(
                    """{"method": "vc", "success": true, "config": {
                         "verifierUrl": "$verifierUrl",
                         "setup": {"flow_type": "cross_device", "core_flow": {"dcql_query": {"credentials": [
                           {"id": "employee", "format": "jwt_vc_json", "meta": {"type_values": [["VerifiableCredential", "EmployeeCredential"]]}}]}}}}}"""
                ),
            )
        )
    }
}

/** The application: [accounts] is your account store, [sendEmailCode] your mail service. */
@OptIn(ExperimentalUuidApi::class)
fun Application.multiTenantApp(
    accounts: EditableAccountStore,
    tenants: TenantDirectory,
    sendEmailCode: suspend (EmailCodeDelivery) -> Unit,
) {
    install(KtorAuthnz) {
        accountStore = accounts
        emailCodes = EmailCodeSettings(send = sendEmailCode)
    }
    install(Authentication) { ktorAuthnz("login") }
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<NotFoundException> { call, cause -> call.respond(HttpStatusCode.NotFound, buildJsonObject { put("error", cause.message) }) }
        // ktor-authnz errors carry their HTTP status: 400, 401, 404, 429, ...
        exception<Throwable> { call, cause ->
            val status = (cause as? HttpStatusError)?.status?.let(HttpStatusCode::fromValue)
                ?: if (cause is IllegalArgumentException) HttpStatusCode.BadRequest else HttpStatusCode.InternalServerError
            call.respond(status, buildJsonObject { put("error", cause.message) })
        }
    }

    routing {
        route("{tenant}") {
            authnzTenant { parameters["tenant"]!! }

            route("auth") {
                authFlows(
                    // Every method any tenant may use, and those a login may start with.
                    methods = listOf(UserPass, TOTP, EmailCode, OIDC, LDAP, VerifiableCredential),
                    firstMethods = listOf(UserPass, OIDC, LDAP, VerifiableCredential),
                    flowsFor = { tenants.flowsOf(authnzTenant!!) },
                ) {
                    // Wallet holders log in with their first presentation, as a new account of the tenant.
                    registerUnknownAccounts(VerifiableCredential) { accounts.addAccountIdentifierToAccount(Uuid.random().toString(), it) }
                }
            }

            authenticate("login") {
                get("me") {
                    val principal = call.authnzPrincipal()!!
                    call.respond(buildJsonObject {
                        put("tenant", principal.tenant)
                        put("account", principal.accountId)
                    })
                }
            }
        }
    }
}
