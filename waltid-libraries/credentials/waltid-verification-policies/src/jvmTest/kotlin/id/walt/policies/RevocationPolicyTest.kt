package id.walt.policies

import id.walt.policies.StatusTestUtils.statusList2021Scenarios
import id.walt.policies.policies.RevocationPolicy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
    private val sut = RevocationPolicy()

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
    fun verify(context: StatusTestUtils.TestContext) = runTest {
        val json = Json.parseToJsonElement(context.credential).jsonObject
        val result = sut.verify(json, null, emptyMap())
        assertEquals(context.expectValid, result.isSuccess)
        assertEquals(!context.expectValid, result.isFailure)
    }


    companion object {
        @JvmStatic
        fun verify(): Stream<Arguments> = statusList2021Scenarios().stream().map { arguments(it) }
    }
}