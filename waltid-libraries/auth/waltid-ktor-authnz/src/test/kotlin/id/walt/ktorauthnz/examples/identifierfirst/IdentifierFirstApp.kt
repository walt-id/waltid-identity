package id.walt.ktorauthnz.examples.identifierfirst

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
import id.walt.ktorauthnz.methods.config.PasskeySettings
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * Example: identifier-first login. The user enters an email address; the answer lists the ways *this* account can log
 * in - those the user set up for it (adding them, e.g. on a security settings page, is up to the application) - and the
 * login continues with one of them:
 *
 *   user1@example.org   password, then TOTP - or a code sent by email
 *   user2@example.org   a passkey - or a verifiable credential from a wallet
 *   user3@example.org   the company identity provider (OIDC) - or the directory (LDAP), then TOTP
 *   *@example.net       always the identity provider of example.net, with or without an account here
 *   other accounts      password (the default for accounts that set up nothing)
 *
 * What is stored where:
 *   - the flow of the login route (here, [loginFlow]): the `identify` step's settings - the settings of the OIDC, LDAP
 *     and VC methods, the example.net rule, and the default
 *   - per account: the flows it offers (`IdentifyStoredData`), its TOTP secret; per identifier: its password,
 *     its passkeys (each under its own credential id), its OIDC, LDAP and wallet identities
 *   - per login session: the identified address (`IdentifiedSessionData`), so later steps need only the password,
 *     email codes go to it and the IdP gets it as `login_hint`; and the account, so no step can switch to another one
 *
 * IdentifierFirstAppTest logs in as each of them, against a mock IdP, a mock verifier, an in-memory LDAP server and an
 * emulated passkey authenticator.
 */

/** The login route's flow: identify, then what the account offers. */
fun loginFlow(oidcDiscoveryUrl: String, ldapUrl: String, verifierUrl: String) = AuthFlow.fromConfig(
    """
    {"method": "identify", "config": {
      "methods": {
        "oidc": {"openIdConfigurationUrl": "$oidcDiscoveryUrl", "clientId": "app", "clientSecret": "app-secret",
                 "callbackUri": "http://localhost/auth/oidc/callback"},
        "ldap": {"ldapServerUrl": "$ldapUrl", "userDNFormat": "mail=%s,ou=people,dc=example"},
        "vc": {"verifierUrl": "$verifierUrl", "setup": {"flow_type": "cross_device", "core_flow": {"dcql_query": {"credentials": [
                 {"id": "employee", "format": "jwt_vc_json", "meta": {"type_values": [["VerifiableCredential", "EmployeeCredential"]]}}]}}}}
      },
      "domains": {
        "example.net": [{"method": "oidc", "success": true}]
      },
      "default": [{"method": "email", "success": true}]
    }}
    """
)

/** The flows each example account set up, as the application stores them when the user adds a method. */
object AccountFlows {
    val passwordAndTotpOrEmailCode = setOf(
        AuthFlow.fromConfig("""{"method": "email", "continue": [{"method": "totp", "success": true}]}"""),
        AuthFlow.fromConfig("""{"method": "email-code", "success": true}"""),
    )
    val passkeyOrWallet = setOf(
        AuthFlow.fromConfig("""{"method": "passkey", "success": true}"""),
        AuthFlow.fromConfig("""{"method": "vc", "success": true}"""),
    )
    val identityProviderOrDirectoryAndTotp = setOf(
        AuthFlow.fromConfig("""{"method": "oidc", "success": true}"""),
        AuthFlow.fromConfig("""{"method": "ldap", "continue": [{"method": "totp", "success": true}]}"""),
    )
}

/** The application: [accounts] is your account store, [sendEmailCode] your mail service. */
fun Application.identifierFirstApp(
    accounts: EditableAccountStore,
    loginFlow: AuthFlow,
    passkeys: PasskeySettings,
    sendEmailCode: suspend (EmailCodeDelivery) -> Unit,
) {
    install(KtorAuthnz) {
        accountStore = accounts
        this.passkeys = passkeys
        emailCodes = EmailCodeSettings(send = sendEmailCode)
    }
    install(Authentication) { ktorAuthnz("login") }
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val status = (cause as? HttpStatusError)?.status?.let(HttpStatusCode::fromValue)
                ?: if (cause is IllegalArgumentException) HttpStatusCode.BadRequest else HttpStatusCode.InternalServerError
            call.respond(status, buildJsonObject { put("error", cause.message) })
        }
    }

    routing {
        route("auth") {
            authFlows(
                // `identify` starts every login; after it, any method an account may have set up.
                methods = listOf(Identify, EmailPass, TOTP, EmailCode, Passkey, VerifiableCredential, OIDC, LDAP),
                firstMethods = listOf(Identify),
                flowsFor = { listOf(loginFlow) },
            )
        }
        authenticate("login") {
            get("me") { call.respond(buildJsonObject { put("account", call.authnzPrincipal()!!.accountId) }) }
        }
    }
}
