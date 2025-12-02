package id.walt.policies2.vc.status.status

import id.walt.credentials.CredentialParser
import id.walt.policies2.vc.policies.RevocationPolicy
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RevocationPolicyTest {
    private val jsonModule = Json { ignoreUnknownKeys = true }

    @BeforeAll
    fun startServer() = runBlocking {
        server.start()
    }

    @AfterAll
    fun stopServer() {
        server.stop()
    }

    @ParameterizedTest
    @MethodSource
    fun verify(context: StatusResourceData) = runTest {
        require(context is SingleStatusResourceData)
        val json = context.holderCredential
        val credential = CredentialParser.parseOnly(rawCredential = jsonModule.encodeToString(json))
        val sut = RevocationPolicy()
        val result = sut.verify(credential = credential)
        assertEquals(expected = context.valid, actual = result.isSuccess)
        assertEquals(expected = !context.valid, actual = result.isFailure)
    }


    companion object {

        private val server = StatusCredentialTestServer()

        @JvmStatic
        fun verify(): Stream<Arguments> = statusList2021Scenarios().stream().map { arguments(it) }

        private fun statusList2021Scenarios() = server.credentials["statuslist2021"]!!.map { it.data }
    }
}
