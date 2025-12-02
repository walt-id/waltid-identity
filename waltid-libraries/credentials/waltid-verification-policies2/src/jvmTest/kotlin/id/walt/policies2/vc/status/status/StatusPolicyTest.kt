package id.walt.policies2.vc.status.status

import id.walt.credentials.CredentialParser
import id.walt.policies2.vc.policies.StatusPolicy
import id.walt.policies2.vc.policies.status.model.W3CStatusPolicyAttribute
import id.walt.policies2.vc.policies.status.model.W3CStatusPolicyListArguments
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
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatusPolicyTest {
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
        val json = context.holderCredential
        val credential = CredentialParser.parseOnly(rawCredential = jsonModule.encodeToString(json))
        val attribute = when (context) {
            is MultiStatusResourceData -> W3CStatusPolicyListArguments(list = context.attribute.map { it as W3CStatusPolicyAttribute })
            is SingleStatusResourceData -> context.attribute
        }
        val sut = StatusPolicy(argument = attribute)
        val result = sut.verify(credential = credential)
        assertEquals(expected = context.valid, actual = result.isSuccess)
        assertEquals(expected = !context.valid, actual = result.isFailure)
        context.exception?.run { assertTrue(actual = result.exceptionOrNull()!!.localizedMessage.contains(this)) }
    }

    companion object {

        private val server = StatusCredentialTestServer()

        @JvmStatic
        fun verify(): Stream<Arguments> = server.credentials.values.flatten().map { it.data }.stream().map { arguments(it) }
    }
}
