
import E2ETestWebService.test
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.webwallet.db.models.Account
import id.walt.webwallet.db.models.AccountWalletListing
import id.walt.webwallet.utils.PKIXUtils
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.KeycloakLogoutRequest
import id.walt.webwallet.web.model.X5CAccountRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.uuid.UUID
import org.bouncycastle.asn1.x500.X500Name
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.util.*
import kotlin.test.assertNotNull


class AuthApi(private val client: HttpClient) {

    suspend fun userInfo(expectedStatus: HttpStatusCode): Account? =
        client.get("/wallet-api/auth/user-info").run {
            assert(status == expectedStatus) { "Expected status: $expectedStatus, but had $status" }
            if (status.isSuccess()) runCatching { body<Account>() }.getOrNull() else null
        }

    suspend fun register(request: AccountRequest) = register(
        client = client,
        name = "/wallet-api/auth/register - wallet-api register",
        url = "/wallet-api/auth/register",
        request = request
    )

    suspend fun login(request: AccountRequest) = client.post("/wallet-api/auth/login") {
        setBody(body = request)
    }.expectSuccess().run {
        body<JsonObject>().let { result ->
            val token = result["token"]
            assertNotNull(token)
            val tokenContent = result["token"]!!.jsonPrimitive.content
            tokenContent.expectLooksLikeJwt()
            tokenContent
        }
    }

    suspend fun logout() =
        client.post("/wallet-api/auth/logout").expectSuccess()

    suspend fun userSession() =
        client.get("/wallet-api/auth/session").expectSuccess()

    suspend fun userWallets(expectedAccountId: UUID) =
        client.get("/wallet-api/wallet/accounts/wallets").expectSuccess().run {
            val listing = body<AccountWalletListing>()
            assert(expectedAccountId == listing.account) { "Wallet listing is for wrong account!" }
            assert(listing.wallets.isNotEmpty()) { "No wallets available!" }
            listing.wallets
        }

    /*class Oidc(private val client: HttpClient) {
        suspend fun oidcToken() = client.get("/wallet-api/auth/oidc-token").expectSuccess()

        suspend fun oidcLogin() = client.get("/wallet-api/auth/oidc-login").expectSuccess()

        suspend fun oidcLogout() = client.get("/wallet-api/auth/oidc-logout").expectSuccess()
    }*/

    /*class Keycloak(private val client: HttpClient) {
        suspend fun token() = client.get("/wallet-api/auth/keycloak/token").expectSuccess().run {
            bodyAsText()
        }

        suspend fun create(request: AccountRequest) = register(
            client = client,
            name = "/wallet-api/auth/keycloak/create - wallet-api keycloak create",
            url = "/wallet-api/auth/keycloak/create",
            request = request
        )

        suspend fun login(request: AccountRequest) = login(
            client = client,
            name = "/wallet-api/auth/keycloak/login - wallet-api keycloak login",
            url = "/wallet-api/auth/keycloak/login",
            request = request,
        )

        suspend fun logout(request: KeycloakLogoutRequest) = client.post("/wallet-api/auth/keycloak/logout") {
                    setBody(request)
                }.expectSuccess()
            }
    }*/

    class X5c(private val client: HttpClient) {

        private val rootCAPrivateKey = PKIXUtils.pemDecodeJavaPrivateKey(
            """
            -----BEGIN PRIVATE KEY-----
            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
            Hlz/QYm+jw2iRfidZxwx9tY+dWjB
            -----END PRIVATE KEY-----
        """.trimIndent()
        )
        private val rootCADistinguishedName = X500Name("CN=RootCA")

        //we don't care about the bit size of the key, it's a test case (as long as it's bigger than 512)
        private val keyPairGenerator = KeyPairGenerator
            .getInstance("RSA").apply {
                initialize(1024)
            }

        private val nonExpiredValidFrom = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
        private val nonExpiredValidTo = Date(System.currentTimeMillis() + 2 * 365 * 24 * 60 * 60 * 1000L)

        private val subjectKeyPair = keyPairGenerator.generateKeyPair()
        private val subjectDistinguishedName = X500Name("CN=SomeSubject")
        private val subjectCert = PKIXUtils.generateSubjectCertificate(
            rootCAPrivateKey,
            subjectKeyPair.public,
            nonExpiredValidFrom,
            nonExpiredValidTo,
            rootCADistinguishedName,
            subjectDistinguishedName,
        )
        private val subjectJWKPrivateKey = PKIXUtils.javaPrivateKeyToJWKKey(subjectKeyPair.private)

        private fun createX5CAccountRequest(key: JWKKey, cert: X509Certificate): AccountRequest = runBlocking {
            X5CAccountRequest(
                null,
                key.signJws(
                    Json.encodeToJsonElement(
                        emptyMap<String, String>()
                    ).toString().toByteArray(),
                    headers = mapOf(
                        "x5c" to listOf(Base64.getEncoder().encodeToString(cert.encoded)).toJsonElement(),
                    ),
                )
            )
        }

        private suspend fun checkX5CLoginCreatesWallet() = test(
            name = "/wallet-api/auth/x5c/login - validate wallet api x5c login with trustworthy subject certificate also creates wallet"
        ) {
            var tempClient = E2ETest.testHttpClient()
            val keyPair = keyPairGenerator.generateKeyPair()
            val dn = X500Name("CN=YeSubject")
            val cert = PKIXUtils.generateSubjectCertificate(
                rootCAPrivateKey,
                keyPair.public,
                nonExpiredValidFrom,
                nonExpiredValidTo,
                rootCADistinguishedName,
                dn,
            )
            val jwkPrivateKey = PKIXUtils.javaPrivateKeyToJWKKey(keyPair.private)
            Companion.login(
                client = tempClient,
                name = "/wallet-api/auth/x5c/login - wallet api x5c login with trustworthy subject certificate",
                url = "/wallet-api/auth/x5c/login",
                request = createX5CAccountRequest(jwkPrivateKey, cert)
            ) {
                tempClient = E2ETest.testHttpClient(token = it["token"]!!.jsonPrimitive.content)
            }
            val response = tempClient.get("/wallet-api/wallet/accounts/wallets").expectSuccess()
            val accWalletListing = response.body<AccountWalletListing>()
            assert(accWalletListing.wallets.isNotEmpty())
        }

        suspend fun executeTestCases() {
            //register with a subject certificate that is signed by the trusted root CA
            Companion.register(
                client = client,
                name = "/wallet-api/auth/x5c/register - wallet api x5c registration with trustworthy subject certificate",
                url = "/wallet-api/auth/x5c/register",
                request = createX5CAccountRequest(subjectJWKPrivateKey, subjectCert)
            )
            //login with a subject certificate that is signed by the trusted root CA
            Companion.login(
                client = client,
                name = "/wallet-api/auth/x5c/login - wallet api x5c login with trustworthy subject certificate",
                url = "/wallet-api/auth/x5c/login",
                request = createX5CAccountRequest(subjectJWKPrivateKey, subjectCert)
            )
            //register with a subject certificate that is not signed by the trusted root CA
            val nonTrustworthyCAKeyPair = keyPairGenerator.generateKeyPair()
            val nonTrustworthySubjectCert = PKIXUtils.generateSubjectCertificate(
                nonTrustworthyCAKeyPair.private,
                subjectKeyPair.public,
                nonExpiredValidFrom,
                nonExpiredValidTo,
                rootCADistinguishedName,
                subjectDistinguishedName,
            )
            test("/wallet-api/auth/x5c/register - wallet api x5c auth registration with non trustworthy subject certificate") {
                client.post("/wallet-api/auth/x5c/register") {
                    setBody(createX5CAccountRequest(subjectJWKPrivateKey, nonTrustworthySubjectCert))
                }.expectFailure()
            }
            //login with a subject certificate that is not signed by the trusted root CA
            test("/wallet-api/auth/x5c/login - wallet api x5c auth login with non trustworthy subject certificate") {
                client.post("/wallet-api/auth/x5c/login") {
                    setBody(createX5CAccountRequest(subjectJWKPrivateKey, nonTrustworthySubjectCert))
                }.expectFailure()
            }
            checkX5CLoginCreatesWallet()
        }
    }

    private companion object {
        suspend fun register(
            client: HttpClient,
            name: String,
            url: String,
            request: AccountRequest,
        ) = test(name) {
            client.post(url) {
                setBody(request)
            }.expectSuccess()
        }
    }
}