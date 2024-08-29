import E2ETestWebService.loadResource
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
import io.ktor.client.statement.*
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
    suspend fun userInfo(expectedStatus: HttpStatusCode, output: ((Account) -> Unit)? = null) =
        test("/wallet-api/auth/user-info - wallet-api user-info") {
            client.get("/wallet-api/auth/user-info").apply {
                assert(status == expectedStatus) { "Expected status: $expectedStatus, but had $status" }
                output?.invoke(body<Account>())
            }
        }

    suspend fun register(request: AccountRequest) = register(
        client = client,
        name = "/wallet-api/auth/register - wallet-api register",
        url = "/wallet-api/auth/register",
        request = request
    )

    suspend fun login(request: AccountRequest, output: ((JsonObject) -> Unit)? = null) = login(
        client = client,
        name = "/wallet-api/auth/login - wallet-api login",
        url = "/wallet-api/auth/login",
        request = request,
        output = output
    )

    suspend fun logout() = test("/wallet-api/auth/logout - wallet-api logout") {
        client.post("/wallet-api/auth/logout").expectSuccess()
    }

    suspend fun userSession() = test("/wallet-api/auth/session - logged in after login") {
        client.get("/wallet-api/auth/session").expectSuccess()
    }

    suspend fun userWallets(expectedAccountId: UUID, output: ((AccountWalletListing) -> Unit)? = null) =
        test("/wallet-api/wallet/accounts/wallets - get wallets") {
            client.get("/wallet-api/wallet/accounts/wallets").expectSuccess().apply {
                val listing = body<AccountWalletListing>()
                assert(expectedAccountId == listing.account) { "Wallet listing is for wrong account!" }
                assert(listing.wallets.isNotEmpty()) { "No wallets available!" }
                output?.invoke(listing)
            }
        }

    class Oidc(private val client: HttpClient) {
        suspend fun oidcToken() = test("/wallet-api/auth/oidc-token - wallet-api oidc token") {
            client.get("/wallet-api/auth/oidc-token").expectSuccess()
        }

        suspend fun oidcLogin() = test("/wallet-api/auth/oidc-login - wallet-api oidc login") {
            client.get("/wallet-api/auth/oidc-login").expectSuccess()
        }

        suspend fun oidcLogout() = test("/wallet-api/auth/logout-oidc - wallet-api oidc logout") {
            client.get("/wallet-api/auth/oidc-logout").expectSuccess()
        }
    }

    class Keycloak(private val client: HttpClient) {
        suspend fun token(output: ((String) -> Unit)? = null) =
            test("/wallet-api/auth/keycloak/token - wallet-api keycloak token") {
                client.get("/wallet-api/auth/keycloak/token").expectSuccess().apply {
                    output?.invoke(bodyAsText())
                }
            }

        suspend fun create(request: AccountRequest) = register(
            client = client,
            name = "/wallet-api/auth/keycloak/create - wallet-api keycloak create",
            url = "/wallet-api/auth/keycloak/create",
            request = request
        )

        suspend fun login(request: AccountRequest, output: ((JsonObject) -> Unit)? = null) = login(
            client = client,
            name = "/wallet-api/auth/keycloak/login - wallet-api keycloak login",
            url = "/wallet-api/auth/keycloak/login",
            request = request,
            output = output
        )

        suspend fun logout(request: KeycloakLogoutRequest) =
            test("/wallet-api/auth/keycloak/logout - wallet-api keycloak logout") {
                client.post("/wallet-api/auth/keycloak/logout") {
                    setBody(request)
                }.expectSuccess()
            }
    }

    class X5c(private val client: HttpClient) {

        private val rootCAPrivateKey = PKIXUtils.pemDecodeJavaPrivateKey(loadResource("x5c/root-ca-priv-key.pem"))
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

        private fun createX5CAccountRequest(key: JWKKey, cert: X509Certificate) = runBlocking {
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

        suspend fun login(
            client: HttpClient,
            name: String,
            url: String,
            request: AccountRequest,
            output: ((JsonObject) -> Unit)? = null,
        ) = test(name) {
            client.post(url) {
                setBody(request)
            }.expectSuccess().apply {
                body<JsonObject>().let { result ->
                    assertNotNull(result["token"])
                    result["token"]!!.jsonPrimitive.content.expectLooksLikeJwt()
                    output?.invoke(result)
                }
            }
        }
    }
}