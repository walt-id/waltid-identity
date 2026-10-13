package id.walt.ktorauthnz.enrollment

import id.walt.errors.HttpStatusError
import id.walt.ktorauthnz.KtorAuthnz
import id.walt.ktorauthnz.accounts.InMemoryAccountStore
import id.walt.ktorauthnz.accounts.identifiers.methods.LDAPIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.OIDCIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.UsernameIdentifier
import id.walt.ktorauthnz.attempts.AttemptLimits
import id.walt.ktorauthnz.auth.ktorAuthnz
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.examples.services.ExampleDirectory
import id.walt.ktorauthnz.examples.services.ExampleIdentityProvider
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.flows.authFlows
import id.walt.ktorauthnz.methods.LDAP
import id.walt.ktorauthnz.methods.OIDC
import id.walt.ktorauthnz.methods.UserPass
import id.walt.ktorauthnz.methods.storeddata.UserPassStoredData
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionNextStepRedirectData
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import id.walt.ktorauthnz.tokens.ktorauthnztoken.KtorAuthNzTokenHandler
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.*

class IdentityLinkingTest {

    private val idp = ExampleIdentityProvider(clientId = "app")
    private val directory = ExampleDirectory(mapOf("alice" to "ldap-secret", "bob" to "bob-ldap"))
    private val accounts = InMemoryAccountStore()
    private val json = Json { ignoreUnknownKeys = true }
    private val alice = runBlocking { accounts.addAccount(UsernameIdentifier("alice"), identifierData = mapOf(UserPass.id to UserPassStoredData("alice-password"))) }
    private val bob = runBlocking { accounts.addAccount(LDAPIdentifier(directory.url, "bob")) }

    @AfterTest
    fun stop() { idp.close(); directory.close() }

    private val oidcFlow = AuthFlow.fromConfig(
        """{"method": "oidc", "success": true, "config": {"openIdConfigurationUrl": "${idp.discoveryUrl}",
            "clientId": "app", "clientSecret": "s", "callbackUri": "http://localhost/auth/oidc/callback"}}"""
    )
    private val ldapFlow = AuthFlow.fromConfig(
        """{"method": "ldap", "success": true, "config": {"ldapServerUrl": "${directory.url}", "userDNFormat": "uid=%s,ou=people,dc=org3"}}"""
    )

    private fun linkTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(KtorAuthnz) {
                accountStore = accounts
                sessionStore = InMemorySessionStore()
                tokenHandler = KtorAuthNzTokenHandler()
                expiringStore = InMemoryExpiringStore()
                attemptLimits = AttemptLimits.DISABLED
            }
            install(Authentication) { ktorAuthnz("login") }
            install(ContentNegotiation) { json() }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    val status = (cause as? HttpStatusError)?.status?.let(HttpStatusCode::fromValue)
                        ?: if (cause is IllegalArgumentException) HttpStatusCode.BadRequest else HttpStatusCode.InternalServerError
                    call.respondText("${cause::class.simpleName}: ${cause.message}", status = status)
                }
            }
            routing {
                route("auth") {
                    authFlows(
                        methods = listOf(UserPass, OIDC, LDAP),
                        firstMethods = listOf(UserPass, OIDC, LDAP),
                        flowsFor = { listOf(AuthFlow.fromConfig("""{"method": "userpass", "success": true}"""), oidcFlow, ldapFlow) },
                    )
                    authenticate("login") { identityLinking(listOf(oidcFlow, ldapFlow)) }
                }
            }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String? = null, token: String? = null) = client.post(path) {
        token?.let { bearerAuth(it) }
        if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
    }

    private fun HttpResponse.info(): AuthSessionInformation = runBlocking {
        assertEquals(HttpStatusCode.OK, status, bodyAsText()); json.decodeFromString(bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.aliceToken() =
        assertNotNull(post("/auth/userpass", """{"username": "alice", "password": "alice-password"}""").info().token)

    /** Runs the IdP part of an OIDC step of [session]; returns the callback's answer. */
    private suspend fun ApplicationTestBuilder.oidc(session: String?, subject: String): HttpResponse {
        val started = client.get(if (session == null) "/auth/oidc/auth" else "/auth/$session/oidc/auth").info()
        val url = (started.nextStep as AuthSessionNextStepRedirectData).url
        idp.subject = subject
        idp.nonce = url.parameters["nonce"]!!
        return client.get("/auth/oidc/callback?code=c&state=${url.parameters["state"]}")
    }

    @Test
    fun `an account links its company identity and then logs in with it`() = linkTest {
        val link = post("/auth/link/oidc", token = aliceToken()).info()
        val linked = oidc(link.id, "alice-at-idp").info()
        assertEquals(AuthSessionStatus.SUCCESS, linked.status)
        assertNull(linked.token, "linking issues no login")
        assertEquals(alice, accounts.lookupAccountUuid(OIDCIdentifier(idp.issuer, "alice-at-idp")))

        val login = oidc(null, "alice-at-idp").info()
        assertEquals(alice, id.walt.ktorauthnz.KtorAuthnzManager.tokenHandler.getTokenAccountId(assertNotNull(login.token)))
    }

    @Test
    fun `an account links its directory login`() = linkTest {
        val link = post("/auth/link/ldap", token = aliceToken()).info()
        assertEquals(AuthSessionStatus.SUCCESS, post("/auth/${link.id}/ldap", """{"username": "alice", "password": "ldap-secret"}""").info().status)
        assertEquals(alice, accounts.lookupAccountUuid(LDAPIdentifier(directory.url, "alice")))
    }

    @Test
    fun `an identity of another account cannot be linked`() = linkTest {
        val link = post("/auth/link/ldap", token = aliceToken()).info()
        assertEquals(HttpStatusCode.Conflict, post("/auth/${link.id}/ldap", """{"username": "bob", "password": "bob-ldap"}""").status)
        assertEquals(bob, accounts.lookupAccountUuid(LDAPIdentifier(directory.url, "bob")))
    }

    @Test
    fun `another account's identity cannot be removed`() = linkTest {
        val removed = client.delete("/auth/identities") {
            bearerAuth(aliceToken()); parameter("type", "ldap"); parameter("value", LDAPIdentifier(directory.url, "bob").toDataString())
        }
        assertEquals(HttpStatusCode.NotFound, removed.status)
        assertEquals(bob, accounts.lookupAccountUuid(LDAPIdentifier(directory.url, "bob")))
    }

    @Test
    fun `linking needs a login`() = linkTest {
        assertEquals(HttpStatusCode.Unauthorized, post("/auth/link/oidc").status)
    }

    @Test
    fun `identities are listed and removed, but not the last one`() = linkTest {
        val token = aliceToken()
        val link = post("/auth/link/ldap", token = token).info()
        post("/auth/${link.id}/ldap", """{"username": "alice", "password": "ldap-secret"}""").info()
        val listed = json.decodeFromString<List<AccountIdentity>>(client.get("/auth/identities") { bearerAuth(token) }.bodyAsText())
        assertEquals(setOf("userpass", "ldap"), listed.map { it.type }.toSet())

        val ldap = listed.single { it.type == "ldap" }
        assertEquals(HttpStatusCode.NoContent, client.delete("/auth/identities") {
            bearerAuth(token); parameter("type", ldap.type); parameter("value", ldap.value)
        }.status)
        assertNull(accounts.lookupAccountUuid(LDAPIdentifier(directory.url, "alice")))
        assertEquals(HttpStatusCode.BadRequest, client.delete("/auth/identities") {
            bearerAuth(token); parameter("type", "userpass"); parameter("value", "alice")
        }.status, "the last one")
    }
}
