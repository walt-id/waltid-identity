package id.walt.policies.policies.status.status

import id.walt.policies.policies.StatusPolicy
import id.walt.policies.policies.status.model.W3CStatusPolicyAttribute
import id.walt.policies.policies.status.model.W3CStatusPolicyListArguments
import id.walt.policies.policies.status.status.StatusCredentialTestServer.credentials
import kotlinx.coroutines.test.runTest
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
class StatusPolicyTest {
    private val sut = StatusPolicy()

    @BeforeAll
    fun startServer() {
        StatusCredentialTestServer.start()
    }

    @AfterAll
    fun stopServer() {
        StatusCredentialTestServer.stop()
    }

    @ParameterizedTest
    @MethodSource
    fun verify(context: StatusResourceData) = runTest {
        val json = context.holderCredential
        val attribute = when (context) {
            is MultiStatusResourceData -> W3CStatusPolicyListArguments(list = context.attribute.map { it as W3CStatusPolicyAttribute })
            is SingleStatusResourceData -> context.attribute
        }
        val result = sut.verify(json, attribute, emptyMap())
        assertEquals(context.valid, result.isSuccess)
        assertEquals(!context.valid, result.isFailure)
    }

    companion object {
        @JvmStatic
        fun verify(): Stream<Arguments> = credentials.values.flatten().map { it.data }.stream().map { arguments(it) }
    }
}