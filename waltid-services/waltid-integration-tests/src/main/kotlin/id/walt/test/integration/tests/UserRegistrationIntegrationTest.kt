@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.test.integration.expectSuccess
import id.walt.test.integration.randomString
import id.walt.webwallet.web.model.EmailAccountRequest
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi

/**
 * Integration tests for user registration functionality.
 * Tests the complete registration flow including validation and error cases.
 */
@TestMethodOrder(OrderAnnotation::class)
class UserRegistrationIntegrationTest : AbstractIntegrationTest() {

    companion object {
        private val testEmail = "test-${randomString(8)}@walt.id"
        private val testPassword = "securePassword123!"
        private val testName = "Test User"
    }

    @Test
    @Order(1)
    fun shouldRegisterNewUser() = runTest {
        val api = environment.getWalletContainerApi()
        val request = EmailAccountRequest(
            name = testName,
            email = testEmail,
            password = testPassword
        )
        
        val response = api.httpClient.post("/wallet-api/auth/register") {
            setBody(request)
        }
        
        assertEquals(HttpStatusCode.Created.value.toDouble(), response.status.value.toDouble(), "Registration should return 201 Created")
    }

    @Test
    @Order(2)
    fun shouldLoginWithRegisteredUser() = runTest {
        val api = environment.getWalletContainerApi()
        val request = EmailAccountRequest(
            name = testName,
            email = testEmail,
            password = testPassword
        )
        
        val loginResponse = api.loginEmailAccountUserRaw(request)
        loginResponse.expectSuccess()
        
        val loginResponseBody = loginResponse.body<JsonObject>()
        assertNotNull(loginResponseBody["id"]?.jsonPrimitive?.content, "Login response should contain user ID")
        assertNotNull(loginResponseBody["token"]?.jsonPrimitive?.content, "Login response should contain token")
        assertEquals(testEmail, loginResponseBody["username"]?.jsonPrimitive?.content, "Username should match email")
    }

    @Test
    @Order(3)
    fun shouldNotRegisterDuplicateUser() = runTest {
        val api = environment.getWalletContainerApi()
        val request = EmailAccountRequest(
            name = testName,
            email = testEmail,
            password = testPassword
        )
        
        val response = api.httpClient.post("/wallet-api/auth/register") {
            setBody(request)
        }
        
        assertEquals(HttpStatusCode.Conflict.value.toDouble(), response.status.value.toDouble(), "Duplicate registration should return 409 Conflict")
    }

    @Test
    @Order(4)
    fun shouldRegisterAnotherUniqueUser() = runTest {
        val api = environment.getWalletContainerApi()
        val uniqueEmail = "unique-${randomString(8)}@walt.id"
        val request = EmailAccountRequest(
            name = "Another User",
            email = uniqueEmail,
            password = "anotherPassword123!"
        )
        
        val response = api.httpClient.post("/wallet-api/auth/register") {
            setBody(request)
        }
        
        assertEquals(HttpStatusCode.Created.value.toDouble(), response.status.value.toDouble(), "Registration with unique email should succeed")
    }
}
