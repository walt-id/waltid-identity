package id.walt.ktorauthnz.examples.identifierfirst

import com.atlassian.onetime.core.TOTPGenerator
import com.atlassian.onetime.model.TOTPSecret
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.InMemoryAccountStore
import id.walt.ktorauthnz.accounts.identifiers.methods.EmailIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.LDAPIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.OIDCIdentifier
import id.walt.ktorauthnz.accounts.identifiers.methods.VerifiableCredentialIdentifier
import id.walt.ktorauthnz.ephemeral.InMemoryExpiringStore
import id.walt.ktorauthnz.examples.services.ExampleAuthenticator
import id.walt.ktorauthnz.examples.services.ExampleDirectory
import id.walt.ktorauthnz.examples.services.ExampleIdentityProvider
import id.walt.ktorauthnz.examples.services.ExampleVerifier
import id.walt.ktorauthnz.methods.EmailPass
import id.walt.ktorauthnz.methods.Identify
import id.walt.ktorauthnz.methods.Passkey
import id.walt.ktorauthnz.methods.TOTP
import id.walt.ktorauthnz.methods.config.EmailCodeDelivery
import id.walt.ktorauthnz.methods.config.PasskeySettings
import id.walt.ktorauthnz.methods.storeddata.EmailPassStoredData
import id.walt.ktorauthnz.methods.storeddata.IdentifyStoredData
import id.walt.ktorauthnz.methods.storeddata.TOTPStoredData
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionNextStepRedirectData
import id.walt.ktorauthnz.sessions.AuthSessionStatus
import id.walt.ktorauthnz.sessions.InMemorySessionStore
import id.walt.ktorauthnz.tokens.ktorauthnztoken.KtorAuthNzTokenHandler
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.*

/** Runs [identifierFirstApp]: each account is offered the ways it set up, and can only log in to itself. */
class IdentifierFirstAppTest {

    private val idp = ExampleIdentityProvider(clientId = "app")
    private val verifier = ExampleVerifier()
    private val directory = ExampleDirectory(mapOf("user3@example.org" to "directory-password"), domain = "example", nameAttribute = "mail")
    private val passkeySettings = PasskeySettings(rpId = "example.org", rpName = "Example", origins = setOf("https://app.example.org"))
    private val authenticator = ExampleAuthenticator("https://app.example.org")
    /** Another user's device, with the passkey of [other]. */
    private val otherAuthenticator = ExampleAuthenticator("https://app.example.org")

    private val accounts = InMemoryAccountStore()
    private val totpSecret = "JBSWY3DPEHPK3PXP"
    private val sentCodes = ConcurrentHashMap<String, EmailCodeDelivery>()
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var user1: String
    private lateinit var user2: String
    private lateinit var user3: String
    private lateinit var user4: String
    private lateinit var other: String

    /** The accounts, with the methods their users set up. */
    @BeforeTest
    fun setUp() = runBlocking {
        KtorAuthnzManager.sessionStore = InMemorySessionStore()
        KtorAuthnzManager.expiringStore = InMemoryExpiringStore()
        KtorAuthnzManager.tokenHandler = KtorAuthNzTokenHandler()
        KtorAuthnzManager.passkeys = passkeySettings

        user1 = accounts.addAccount(
            EmailIdentifier("user1@example.org"),
            identifierData = mapOf(EmailPass.id to EmailPassStoredData(password = "user1-password")),
            accountData = mapOf(TOTP.id to TOTPStoredData(totpSecret), Identify.id to IdentifyStoredData(AccountFlows.passwordAndTotpOrEmailCode)),
        )
        user2 = accounts.addAccount(
            EmailIdentifier("user2@example.org"),
            VerifiableCredentialIdentifier("credentialSubject/id", "did:key:user2"),
            accountData = mapOf(Identify.id to IdentifyStoredData(AccountFlows.passkeyOrWallet)),
        )
        registerPasskey(user2, authenticator)
        user3 = accounts.addAccount(
            EmailIdentifier("user3@example.org"),
            OIDCIdentifier(idp.issuer, "idp-user3"),
            LDAPIdentifier(directory.url, "user3@example.org"),
            accountData = mapOf(TOTP.id to TOTPStoredData(totpSecret), Identify.id to IdentifyStoredData(AccountFlows.identityProviderOrDirectoryAndTotp)),
        )
        user4 = accounts.addAccount(
            EmailIdentifier("user4@example.org"),
            identifierData = mapOf(EmailPass.id to EmailPassStoredData(password = "user4-password")),
        )
        other = accounts.addAccount(EmailIdentifier("other@example.org"))
        registerPasskey(other, otherAuthenticator)
    }

    @AfterTest
    fun tearDown() {
        idp.close(); verifier.close(); directory.close()
        KtorAuthnzManager.passkeys = null
    }

    /** What the application's "add a passkey" page does, done directly: registers a passkey of [accountId]. */
    private suspend fun registerPasskey(accountId: String, device: ExampleAuthenticator) {
        val options = Passkey.registrationOptions(Passkey.newChallenge("register:$accountId"), accountId, accountId, emptyList())
        val (identifier, stored) = Passkey.verifyRegistration(device.create(options), accountId, null)
        accounts.addAccountIdentifierToAccount(accountId, identifier)
        accounts.addAccountIdentifierStoredData(identifier, Passkey.id, stored)
    }

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            identifierFirstApp(accounts, loginFlow(idp.discoveryUrl, directory.url, verifier.url), passkeySettings) {
                sentCodes[it.email!!] = it
            }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String? = null) = client.post(path) {
        if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
    }

    private fun HttpResponse.session(): AuthSessionInformation = runBlocking {
        assertEquals(HttpStatusCode.OK, status, bodyAsText()); json.decodeFromString(bodyAsText())
    }

    private fun totp() = """{"code": "${TOTPGenerator().generateCurrent(TOTPSecret.fromBase32EncodedString(totpSecret)).value}"}"""

    private suspend fun ApplicationTestBuilder.identify(email: String) = post("/auth/identify", """{"email": "$email"}""")

    private suspend fun ApplicationTestBuilder.accountOf(done: AuthSessionInformation): String {
        assertEquals(AuthSessionStatus.SUCCESS, done.status)
        val me = client.get("/me") { bearerAuth(assertNotNull(done.token)) }.bodyAsText()
        return json.parseToJsonElement(me).jsonObject["account"].toString().trim('"')
    }

    private suspend fun ApplicationTestBuilder.passkeyAssertion(session: String): String {
        val options = json.parseToJsonElement(post("/auth/$session/passkey/options").bodyAsText()).jsonObject
        return authenticator.get(options)
    }

    @Test
    fun `each account is offered the ways it set up`() = app {
        assertEquals(setOf("email", "email-code"), identify("user1@example.org").session().nextMethod?.toSet())
        assertEquals(setOf("passkey", "vc"), identify("user2@example.org").session().nextMethod?.toSet())
        assertEquals(setOf("oidc", "ldap"), identify("user3@example.org").session().nextMethod?.toSet())
        assertEquals(listOf("oidc"), identify("anyone@example.net").session().nextMethod)
        assertEquals(listOf("email"), identify("user4@example.org").session().nextMethod, "the default")
        assertEquals(HttpStatusCode.NotFound, identify("nobody@example.org").status)
    }

    @Test
    fun `user1 logs in with the password - only the password, the address is known - then TOTP`() = app {
        val session = identify("user1@example.org").session().id
        assertEquals(listOf("totp"), post("/auth/$session/email", """{"password": "user1-password"}""").session().nextMethod)
        assertEquals(user1, accountOf(post("/auth/$session/totp", totp()).session()))
    }

    @Test
    fun `user1 logs in with a code sent to the identified address`() = app {
        val session = identify("user1@example.org").session().id
        post("/auth/$session/email-code/send").session()
        val code = assertNotNull(sentCodes["user1@example.org"]).code
        assertEquals(user1, accountOf(post("/auth/$session/email-code", """{"code": "$code"}""").session()))
    }

    @Test
    fun `user2 logs in with a passkey`() = app {
        val session = identify("user2@example.org").session().id
        assertEquals(user2, accountOf(post("/auth/$session/passkey", passkeyAssertion(session)).session()))
    }

    @Test
    fun `user2 logs in with a credential from the wallet`() = app {
        val session = identify("user2@example.org").session().id
        verifier.holder = "did:key:user2"
        post("/auth/$session/vc/start").session()
        verifier.presented = true
        assertEquals(user2, accountOf(client.get("/auth/$session/vc/status").session()))
    }

    @Test
    fun `user3 logs in with the identity provider, which is told who is logging in`() = app {
        val session = identify("user3@example.org").session().id
        val authorizationUrl = (client.get("/auth/$session/oidc/auth").session().nextStep as AuthSessionNextStepRedirectData).url
        assertEquals("user3@example.org", authorizationUrl.parameters["login_hint"])
        idp.subject = "idp-user3"
        idp.nonce = assertNotNull(authorizationUrl.parameters["nonce"])
        assertEquals(user3, accountOf(client.get("/auth/oidc/callback?code=c&state=${authorizationUrl.parameters["state"]}").session()))
    }

    @Test
    fun `user3 logs in against the directory, then with TOTP`() = app {
        val session = identify("user3@example.org").session().id
        post("/auth/$session/ldap", """{"password": "directory-password"}""").session()
        assertEquals(user3, accountOf(post("/auth/$session/totp", totp()).session()))
    }

    @Test
    fun `everyone at example_net logs in with its identity provider, getting an account on the first login`() = app {
        val session = identify("new.colleague@example.net").session().id
        val authorizationUrl = (client.get("/auth/$session/oidc/auth").session().nextStep as AuthSessionNextStepRedirectData).url
        idp.subject = "idp-new-colleague"
        idp.nonce = assertNotNull(authorizationUrl.parameters["nonce"])
        val account = accountOf(client.get("/auth/oidc/callback?code=c&state=${authorizationUrl.parameters["state"]}").session())
        assertEquals(account, accounts.lookupAccountUuid(OIDCIdentifier(idp.issuer, "idp-new-colleague")))
    }

    @Test
    fun `a login cannot switch to another account`() = app {
        // A valid passkey, but of another account than the identified one.
        val user2Session = identify("user2@example.org").session().id
        val options = json.parseToJsonElement(post("/auth/$user2Session/passkey/options").bodyAsText()).jsonObject
        assertEquals(HttpStatusCode.Unauthorized, post("/auth/$user2Session/passkey", otherAuthenticator.get(options)).status)

        // A password step logs in the identified address only.
        val user1Session = identify("user1@example.org").session().id
        val switched = post("/auth/$user1Session/email", """{"email": "user4@example.org", "password": "user4-password"}""")
        assertEquals(HttpStatusCode.BadRequest, switched.status)
    }
}
