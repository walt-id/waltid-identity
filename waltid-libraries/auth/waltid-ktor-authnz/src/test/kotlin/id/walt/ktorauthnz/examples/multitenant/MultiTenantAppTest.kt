package id.walt.ktorauthnz.examples.multitenant

import com.atlassian.onetime.core.TOTPGenerator
import com.atlassian.onetime.model.TOTPSecret
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.InMemoryAccountStore
import id.walt.ktorauthnz.examples.services.ExampleDirectory
import id.walt.ktorauthnz.examples.services.ExampleIdentityProvider
import id.walt.ktorauthnz.examples.services.ExampleVerifier
import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.LDAPIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.UsernameIdentifier
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.methods.TOTP
import id.walt.ktorauthnz.methods.UserPass
import id.walt.ktorauthnz.methods.config.EmailCodeDelivery
import id.walt.ktorauthnz.methods.storeddata.TOTPStoredData
import id.walt.ktorauthnz.methods.storeddata.UserPassStoredData
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionNextStepRedirectData
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import id.walt.ktorauthnz.tenants.inAuthnzTenant
import id.walt.ktorauthnz.tokens.ktorauthnztoken.KtorAuthNzTokenHandler
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.*

/** Runs [multiTenantApp] for its four tenants, each with its own login, and checks that the tenants stay apart. */
class MultiTenantAppTest {

    private val idp = ExampleIdentityProvider(clientId = "org2-app")
    private val verifier = ExampleVerifier()
    private val directory = ExampleDirectory(mapOf("carol" to "ldap-secret"))

    private val accounts = InMemoryAccountStore()
    private val tenants = TenantDirectory.example(idp.discoveryUrl, directory.url, verifier.url)
    private val totpSecret = "JBSWY3DPEHPK3PXP"

    /** The "mail service": the last code sent per account. */
    private val sentCodes = ConcurrentHashMap<String, EmailCodeDelivery>()

    private lateinit var alice: String
    private lateinit var carol: String

    @BeforeTest
    fun setUp() = runBlocking {
        KtorAuthnzManager.sessionStore = InMemorySessionStore()
        KtorAuthnzManager.expiringStore = InMemoryExpiringStore()
        KtorAuthnzManager.tokenHandler = KtorAuthNzTokenHandler()

        alice = inAuthnzTenant("org1") {
            accounts.addAccount(
                UsernameIdentifier("alice"),
                identifierData = mapOf(UserPass.id to UserPassStoredData("alice-password")),
                accountData = mapOf(TOTP.id to TOTPStoredData(totpSecret)),
            )
        }
        carol = inAuthnzTenant("org3") {
            accounts.addAccount(LDAPIdentifier(directory.url, "carol"), accountData = mapOf(TOTP.id to TOTPStoredData(totpSecret)))
        }
    }

    @AfterTest
    fun tearDown() {
        idp.close(); verifier.close(); directory.close()
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { multiTenantApp(accounts, tenants) { sentCodes[it.accountId ?: it.email!!] = it } }
        block()
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String? = null) = client.post(path) {
        if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
    }

    private fun HttpResponse.session(): AuthSessionInformation = runBlocking {
        assertEquals(HttpStatusCode.OK, status, bodyAsText())
        json.decodeFromString(bodyAsText())
    }

    private fun totp() = """{"code": "${TOTPGenerator().generateCurrent(TOTPSecret.fromBase32EncodedString(totpSecret)).value}"}"""

    private suspend fun ApplicationTestBuilder.me(tenant: String, token: String) = client.get("/$tenant/me") { bearerAuth(token) }

    private suspend fun ApplicationTestBuilder.loginAlice(): String {
        val password = post("/org1/auth/userpass", """{"username": "alice", "password": "alice-password"}""").session()
        assertEquals(listOf("totp"), password.nextMethod)
        val second = post("/org1/auth/${password.id}/totp", totp()).session()
        assertEquals(listOf("email-code"), second.nextMethod)

        post("/org1/auth/${password.id}/email-code/send").session()
        val code = assertNotNull(sentCodes[alice]).code
        val done = post("/org1/auth/${password.id}/email-code", """{"code": "$code"}""").session()
        assertEquals(AuthSessionStatus.SUCCESS, done.status)
        return assertNotNull(done.token)
    }

    @Test
    fun `org1 logs in with password, then TOTP, then a code sent by email`() = app {
        val token = loginAlice()
        val me = json.parseToJsonElement(me("org1", token).bodyAsText()).jsonObject
        assertEquals("org1", me["tenant"]?.jsonPrimitive?.content)
        assertEquals(alice, me["account"]?.jsonPrimitive?.content)
    }

    @Test
    fun `org2 logs in with its identity provider, creating the account on first login`() = app {
        val started = client.get("/org2/auth/oidc/auth").session()
        val authorizationUrl = (started.nextStep as AuthSessionNextStepRedirectData).url
        idp.nonce = assertNotNull(authorizationUrl.parameters["nonce"])

        val done = client.get("/org2/auth/oidc/callback?code=c&state=${authorizationUrl.parameters["state"]}").session()
        assertEquals(AuthSessionStatus.SUCCESS, done.status)
        assertEquals(HttpStatusCode.OK, me("org2", assertNotNull(done.token)).status)
    }

    @Test
    fun `org3 logs in against its directory, then with TOTP`() = app {
        val ldap = post("/org3/auth/ldap", """{"username": "carol", "password": "ldap-secret"}""").session()
        val done = post("/org3/auth/${ldap.id}/totp", totp()).session()
        assertEquals(AuthSessionStatus.SUCCESS, done.status)
        assertTrue(me("org3", assertNotNull(done.token)).bodyAsText().contains(carol))

        assertEquals(HttpStatusCode.Unauthorized, post("/org3/auth/ldap", """{"username": "carol", "password": "wrong"}""").status)
    }

    @Test
    fun `org4 logs in by presenting a credential from a wallet`() = app {
        val started = post("/org4/auth/vc/start").session()
        assertEquals(AuthSessionStatus.CONTINUE_NEXT_STEP, started.status, "the wallet has not presented yet")
        verifier.presented = true

        val done = client.get("/org4/auth/${started.id}/vc/status").session()
        assertEquals(AuthSessionStatus.SUCCESS, done.status)
        assertEquals(HttpStatusCode.OK, me("org4", assertNotNull(done.token)).status)
    }

    @Test
    fun `a token of one tenant is not accepted by another`() = app {
        val token = loginAlice()
        assertEquals(HttpStatusCode.OK, me("org1", token).status)
        assertEquals(HttpStatusCode.Unauthorized, me("org3", token).status)
    }

    @Test
    fun `a login only starts with what the tenant configured`() = app {
        assertEquals(HttpStatusCode.BadRequest, post("/org3/auth/userpass", """{"username": "alice", "password": "alice-password"}""").status)
        assertEquals(HttpStatusCode.NotFound, post("/unknown/auth/userpass", """{"username": "alice", "password": "alice-password"}""").status)
    }

    @Test
    fun `accounts belong to their tenant`() = runBlocking {
        // org5 has its own alice; org1's password does not open it, and org1's alice is untouched.
        val other = inAuthnzTenant("org5") { accounts.addAccount(UsernameIdentifier("alice"), EmailIdentifier("alice@org5.example")) }
        assertNotEquals(alice, other)
        assertEquals(alice, inAuthnzTenant("org1") { accounts.lookupAccountUuid(UsernameIdentifier("alice")) })
        assertNull(inAuthnzTenant("org5") { accounts.lookupStoredDataForAccountIdentifier(UsernameIdentifier("alice"), UserPass) })
    }

    @Test
    fun `a session of one tenant cannot be continued at another`() = app {
        val password = post("/org1/auth/userpass", """{"username": "alice", "password": "alice-password"}""").session()
        assertEquals(HttpStatusCode.NotFound, post("/org3/auth/${password.id}/totp", totp()).status)
    }
}
