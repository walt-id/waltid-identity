@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.test.integration.expectFailure
import id.walt.test.integration.expectLooksLikeJwt
import id.walt.test.integration.expectSuccess
import id.walt.test.integration.randomString
import id.walt.webwallet.utils.PKIXUtils
import id.walt.webwallet.web.model.X5CAccountRequest
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.bouncycastle.asn1.x500.X500Name
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi

@TestMethodOrder(OrderAnnotation::class)
class X5cUserWalletIntegrationTest : AbstractIntegrationTest() {

    private val e2e = environment.e2e
    private val walletContainerApi = environment.getWalletContainerApi()
    private val client = walletContainerApi.httpClient

    companion object {
        //we don't care about the bit size of the key, it's a test case (as long as it's bigger than 512)
        private val keyPairGenerator = KeyPairGenerator
            .getInstance("RSA").apply {
                initialize(2048)
            }

        private val subjectKeyPair = keyPairGenerator.generateKeyPair()
        private val subjectJWKPrivateKey = PKIXUtils.javaPrivateKeyToJWKKey(subjectKeyPair.private)

        private val rootCAPrivateKey = PKIXUtils.pemDecodeJavaPrivateKey(
            """
            -----BEGIN PRIVATE KEY-----
            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
            Hlz/QYm+jw2iRfidZxwx9tY+dWjB
            -----END PRIVATE KEY-----
        """.trimIndent()
        )
        private val rootCADistinguishedName = X500Name("CN=RootCA")

        private val nonExpiredValidFrom = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
        private val nonExpiredValidTo = Date(System.currentTimeMillis() + 2 * 365 * 24 * 60 * 60 * 1000L)

        private val subjectDistinguishedName = X500Name("CN=SomeSubject")


        private val subjectCert = PKIXUtils.generateSubjectCertificate(
            rootCAPrivateKey,
            subjectKeyPair.public,
            nonExpiredValidFrom,
            nonExpiredValidTo,
            rootCADistinguishedName,
            subjectDistinguishedName,
        )
    }

    @Test
    @Order(1)
    fun shouldRegisterX5cUserWithTrustworthySubjectCertificate() = runTest {
        //register with a subject certificate that is signed by the trusted root CA
        val response = walletContainerApi.registerX5cUserRaw(createX5CAccountRequest(subjectJWKPrivateKey, subjectCert))
        response.expectSuccess()
        assertNotNull(response.body<String>()).also {
            assertEquals("Registration succeeded", it.trim())
        }
    }

    @Test
    @Order(2)
    fun shouldLoginX5cUserWithTrustworthySubjectCertificate() = runTest {
        //login with a subject certificate that is signed by the trusted root CA
        val response = walletContainerApi.loginX5cUserRaw(createX5CAccountRequest(subjectJWKPrivateKey, subjectCert))
        response.expectSuccess()
        assertNotNull(response.body<JsonObject>()).also {
            assertNotNull(it["id"])
            assertNotNull(it["token"]?.jsonPrimitive?.content).also { token ->
                token.expectLooksLikeJwt()
            }
        }
    }

    @Test
    @Order(3)
    fun shouldNotRegisterX5cUserWithNonTrustworthySubjectCertificate() = runTest {
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
        val response = walletContainerApi.registerX5cUserRaw(
            createX5CAccountRequest(subjectJWKPrivateKey, nonTrustworthySubjectCert)
        )
        response.expectFailure()
    }


    @Test
    @Order(4)
    fun shouldNotLoginX5cUserWithNonTrustworthySubjectCertificate() = runTest {

        val nonTrustworthyCAKeyPair = keyPairGenerator.generateKeyPair()
        val nonTrustworthySubjectCert = PKIXUtils.generateSubjectCertificate(
            nonTrustworthyCAKeyPair.private,
            subjectKeyPair.public,
            nonExpiredValidFrom,
            nonExpiredValidTo,
            rootCADistinguishedName,
            subjectDistinguishedName,
        )


        //login with a subject certificate that is not signed by the trusted root CA
        e2e.test("/wallet-api/auth/x5c/login - wallet api x5c auth login with non trustworthy subject certificate") {
            client.post("/wallet-api/auth/x5c/login") {
                setBody(createX5CAccountRequest(subjectJWKPrivateKey, nonTrustworthySubjectCert))
            }.expectFailure()
        }
    }

    @Test
    fun shouldAllowLoginAndCreateNewWalletWhenX5cUserLogsInFirstTime() = runTest {
        //name = "/wallet-api/auth/x5c/login - validate wallet api x5c login with trustworthy subject certificate also creates wallet"
        val subjectString = "CN=${randomString(5)}"
        val keyPair = keyPairGenerator.generateKeyPair()
        val dn = X500Name(subjectString)
        val cert = PKIXUtils.generateSubjectCertificate(
            rootCAPrivateKey,
            keyPair.public,
            nonExpiredValidFrom,
            nonExpiredValidTo,
            rootCADistinguishedName,
            dn,
        )
        val jwkPrivateKey = PKIXUtils.javaPrivateKeyToJWKKey(keyPair.private)
        val authenticatedWalletApi = walletContainerApi.login(createX5CAccountRequest(jwkPrivateKey, cert))
        val userInfo = authenticatedWalletApi.userInfo()
        assertNotNull(userInfo.id) { "User info is null" }
        assertNotNull(authenticatedWalletApi.listAccountWallets()).also { wallets ->
            assertEquals(userInfo.id, wallets.account)
            assertFalse(wallets.wallets.isEmpty())
        }
    }
}

private fun createX5CAccountRequest(key: JWKKey, cert: X509Certificate): X5CAccountRequest = runBlocking {
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
