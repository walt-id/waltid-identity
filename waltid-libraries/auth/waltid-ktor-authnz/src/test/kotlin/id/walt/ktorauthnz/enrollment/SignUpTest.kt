package id.walt.ktorauthnz.enrollment

import id.walt.errors.HttpStatusError
import id.walt.ktorauthnz.KtorAuthnz
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.EditableAccountStore
import id.walt.ktorauthnz.accounts.InMemoryAccountStore
import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.methods.storeddata.AuthMethodStoredData
import id.walt.ktorauthnz.accounts.RegisteredAccount
import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.OIDCIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.UsernameIdentifier
import id.walt.ktorauthnz.accounts.registerAccount
import id.walt.ktorauthnz.attempts.AttemptLimits
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.examples.services.ExampleIdentityProvider
import id.walt.ktorauthnz.exceptions.AccountExistsException
import id.walt.ktorauthnz.flows.AuthFlow
import id.walt.ktorauthnz.flows.authFlows
import id.walt.ktorauthnz.methods.EmailPass
import id.walt.ktorauthnz.methods.UserPass
import id.walt.ktorauthnz.methods.config.EmailCodeDelivery
import id.walt.ktorauthnz.methods.config.EmailCodeSettings
import id.walt.ktorauthnz.methods.storeddata.EmailPassStoredData
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
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class SignUpTest {

    private val accounts = InMemoryAccountStore()
    private val sent = mutableListOf<EmailCodeDelivery>()
    private val registered = mutableListOf<RegisteredAccount>()
    private val json = Json { ignoreUnknownKeys = true }
    private val idp = ExampleIdentityProvider(clientId = "app")

    @AfterTest
    fun tearDown() {
        idp.close()
        KtorAuthnzManager.onAccountRegistered = null
        KtorAuthnzManager.emailCodes = null
    }

    private fun signUpTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(KtorAuthnz) {
                accountStore = accounts
                sessionStore = InMemorySessionStore()
                tokenHandler = KtorAuthNzTokenHandler()
                expiringStore = InMemoryExpiringStore()
                attemptLimits = AttemptLimits()
                emailCodes = EmailCodeSettings { sent += it }
                onAccountRegistered { registered += it }
            }
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
                    authFlows(listOf(
                        AuthFlow.fromConfig("""{"method": "email", "success": true}"""),
                        AuthFlow.fromConfig("""{"method": "userpass", "success": true}"""),
                        AuthFlow.fromConfig(
                            """{"method": "oidc", "success": true, "config": {"openIdConfigurationUrl": "${idp.discoveryUrl}",
                                "clientId": "app", "clientSecret": "s", "callbackUri": "http://localhost/auth/oidc/callback"}}"""
                        ),
                    ))
                }
                route("email") { signUp(EmailPass) }
                route("username") { signUp(UserPass) }
            }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String) =
        client.post(path) { contentType(ContentType.Application.Json); setBody(body) }

    private fun HttpResponse.text() = runBlocking { bodyAsText() }

    @Test
    fun `a username sign-up creates the account at once, which then logs in`() = signUpTest {
        val created = post("/username/signup", """{"username": "bob", "password": "bob-password", "displayName": "Bob"}""")
        assertEquals(HttpStatusCode.Created, created.status, created.text())
        val accountId = json.parseToJsonElement(created.text()).jsonObject["account_id"]!!.jsonPrimitive.content

        val login = json.decodeFromString<AuthSessionInformation>(post("/auth/userpass", """{"username": "bob", "password": "bob-password"}""").text())
        assertEquals(AuthSessionStatus.SUCCESS, login.status)
        assertEquals(accountId, KtorAuthnzManager.tokenHandler.getTokenAccountId(login.token!!))

        val hook = registered.single()
        assertEquals(accountId, hook.accountId)
        assertEquals("Bob", hook.details!!["displayName"]!!.jsonPrimitive.content)
        assertNull(hook.details!!["password"], "the password is not a detail")
    }

    @Test
    fun `a taken login name and a short password are refused`() = signUpTest {
        assertEquals(HttpStatusCode.Created, post("/username/signup", """{"username": "bob", "password": "bob-password"}""").status)
        assertEquals(HttpStatusCode.Conflict, post("/username/signup", """{"username": "bob", "password": "other-password"}""").status)
        assertEquals(HttpStatusCode.BadRequest, post("/username/signup", """{"username": "carol", "password": "short"}""").status)
    }

    @Test
    fun `an email sign-up waits for the code sent to the address`() = signUpTest {
        val pending = post("/email/signup", """{"email": "dana@example.com", "password": "dana-password"}""")
        assertEquals(HttpStatusCode.Accepted, pending.status, pending.text())
        val signUpId = json.parseToJsonElement(pending.text()).jsonObject["signup_id"]!!.jsonPrimitive.content
        val delivery = sent.single()
        assertEquals("dana@example.com", delivery.email)
        assertNull(accounts.lookupAccountUuid(EmailIdentifier("dana@example.com")), "nothing is created before the code")

        assertEquals(HttpStatusCode.Unauthorized, post("/email/signup/confirm", """{"signup_id": "$signUpId", "code": "000000x"}""").status)
        val confirmed = post("/email/signup/confirm", """{"signup_id": "$signUpId", "code": "${delivery.code}"}""")
        assertEquals(HttpStatusCode.Created, confirmed.status, confirmed.text())
        assertEquals(HttpStatusCode.Unauthorized, post("/email/signup/confirm", """{"signup_id": "$signUpId", "code": "${delivery.code}"}""").status)

        val stored = accounts.lookupStoredDataForAccountIdentifier(EmailIdentifier("dana@example.com"), EmailPass) as EmailPassStoredData
        assertNull(stored.password, "kept hashed only")
        assertEquals(AuthSessionStatus.SUCCESS, json.decodeFromString<AuthSessionInformation>(
            post("/auth/email", """{"email": "dana@example.com", "password": "dana-password"}""").text()
        ).status)
    }

    @Test
    fun `an address with an account gets no code`() = signUpTest {
        runBlocking { accounts.addAccount(EmailIdentifier("taken@example.com")) }
        assertEquals(HttpStatusCode.Conflict, post("/email/signup", """{"email": "taken@example.com", "password": "some-password"}""").status)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `registering hashes passwords, whatever the store does`() = runBlocking<Unit> {
        val written = mutableListOf<AuthMethodStoredData>()
        KtorAuthnzManager.accountStore = object : EditableAccountStore by accounts {
            override suspend fun addAccountIdentifierStoredData(accountIdentifier: AccountIdentifier, method: String, data: AuthMethodStoredData) {
                written += data
            }
        }
        registerAccount { password(EmailIdentifier("hash@example.com"), "plain-password") }
        val stored = written.single() as EmailPassStoredData
        assertNull(stored.password)
        assertNotNull(stored.passwordHash)
    }

    @Test
    fun `wrong codes end a sign-up`() = signUpTest {
        val pending = post("/email/signup", """{"email": "eve@example.com", "password": "eve-password"}""")
        val signUpId = json.parseToJsonElement(pending.text()).jsonObject["signup_id"]!!.jsonPrimitive.content
        repeat(5) { post("/email/signup/confirm", """{"signup_id": "$signUpId", "code": "999999x"}""") }
        assertNotEquals(HttpStatusCode.Created, post("/email/signup/confirm", """{"signup_id": "$signUpId", "code": "${sent.single().code}"}""").status)
        assertNull(accounts.lookupAccountUuid(EmailIdentifier("eve@example.com")))
    }

    @Test
    fun `a new OIDC identity is registered through the same hook, with its claims`() = signUpTest {
        val started = json.decodeFromString<AuthSessionInformation>(client.get("/auth/oidc/auth").text())
        val url = (started.nextStep as AuthSessionNextStepRedirectData).url
        idp.subject = "new-colleague"
        idp.nonce = url.parameters["nonce"]!!
        assertEquals(HttpStatusCode.OK, client.get("/auth/oidc/callback?code=c&state=${url.parameters["state"]}").status)

        val hook = registered.single()
        assertEquals(listOf(OIDCIdentifier(idp.issuer, "new-colleague")), hook.identifiers)
        assertEquals("new-colleague", hook.details!!["sub"]!!.jsonPrimitive.content)
    }

    @Test
    fun `registering checks every identifier before writing any`() = runBlocking {
        KtorAuthnzManager.accountStore = accounts
        val existing = registerAccount { identifier(UsernameIdentifier("taken")) }
        assertFailsWith<AccountExistsException> {
            registerAccount { password(EmailIdentifier("fresh@example.com"), "fresh-password"); identifier(UsernameIdentifier("taken")) }
        }
        assertNull(accounts.lookupAccountUuid(EmailIdentifier("fresh@example.com")))
        assertEquals(existing, accounts.lookupAccountUuid(UsernameIdentifier("taken")))
    }
}
