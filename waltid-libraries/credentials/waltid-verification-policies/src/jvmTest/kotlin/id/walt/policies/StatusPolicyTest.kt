package id.walt.policies

import id.walt.policies.StatusCredentialTestServer.credentials
import id.walt.policies.StatusTestUtils.StatusTestContext
import id.walt.policies.policies.StatusPolicy
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
    fun verify(context: StatusTestContext) = runTest {
        val json = context.credential
        val result = sut.verify(json, context.attribute, emptyMap())
        assertEquals(context.expectValid, result.isSuccess)
        assertEquals(!context.expectValid, result.isFailure)
    }

    companion object {
        @JvmStatic
        fun verify(): Stream<Arguments> = credentials.values.flatten().map {
            StatusTestContext(
                credential = it.data.holderCredential,
                attribute = it.data.attribute,
                expectValid = it.data.valid
            )
        }.stream().map { arguments(it) }
    }
}