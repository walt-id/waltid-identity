package id.walt.policies.policies.status.status

import id.walt.policies.policies.StatusPolicy
import id.walt.webdatafetching.ssrf.BlockedAddressException
import id.walt.webdatafetching.ssrf.PrivateNetworkGuardSettings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The status list URL comes from the credential being verified, so the status policy must not fetch
 * loopback, private-network or cloud-metadata addresses.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatusPolicyEgressGuardTest {
    private val server = StatusCredentialTestServer()
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeAll
    fun startServer() = runBlocking { server.start() }

    @AfterAll
    fun stopServer() = server.stop()

    @AfterEach
    fun resetGuard() = PrivateNetworkGuardSettings.clearAllowLoopbackChoice()

    private fun validCase(): SingleStatusResourceData =
        server.credentials.getValue("statuslist2021").map { it.data }
            .filterIsInstance<SingleStatusResourceData>().first { it.valid }

    private suspend fun verify(case: SingleStatusResourceData, rewriteBaseUrlTo: String? = null): Result<*> {
        var raw = json.encodeToString(case.holderCredential)
        if (rewriteBaseUrlTo != null) raw = raw.replace("http://localhost:${server.port}", rewriteBaseUrlTo)
        return StatusPolicy().verify(
            json.decodeFromString<JsonObject>(raw),
            json.encodeToJsonElement(case.attribute),
            emptyMap(),
        )
    }

    private fun assertBlocked(result: Result<*>) {
        val error = result.exceptionOrNull()
        assertTrue(result.isFailure, "status policy must fail when the status list address is blocked")
        assertTrue(
            generateSequence(error) { it.cause }.any { it is BlockedAddressException } ||
                error?.message.orEmpty().contains("blocked"),
            "expected a blocked-address failure, got: $error",
        )
    }

    @Test
    fun `fetches the status list when loopback is allowed`() = runTest {
        PrivateNetworkGuardSettings.allowLoopback = true
        val result = verify(validCase())
        assertTrue(result.isSuccess, "control case failed: ${result.exceptionOrNull()}")
    }

    @Test
    fun `refuses a loopback status list`() = runTest {
        PrivateNetworkGuardSettings.allowLoopback = false
        assertBlocked(verify(validCase()))
    }

    @Test
    fun `refuses the cloud metadata address even when loopback is allowed`() = runTest {
        PrivateNetworkGuardSettings.allowLoopback = true
        assertBlocked(verify(validCase(), rewriteBaseUrlTo = "http://169.254.169.254"))
    }
}
