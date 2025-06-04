package id.walt.policies

import id.walt.policies.StatusTestUtils.statusList2021Scenarios
import id.walt.policies.policies.StatusPolicy
import id.walt.policies.policies.status.Values.STATUS_LIST_2021
import id.walt.policies.policies.status.model.W3CStatusPolicyAttribute
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
    fun verify(context: StatusTestUtils.TestContext) = runTest {
        val json = Json.parseToJsonElement(context.credential).jsonObject
        val result = sut.verify(json, context.attribute, emptyMap())
        assertEquals(context.expectValid, result.isSuccess)
        assertEquals(!context.expectValid, result.isFailure)
    }

    companion object {
        @JvmStatic
        fun verify(): Stream<Arguments> = statusList2021Scenarios(
            W3CStatusPolicyAttribute(
                value = 0u,
                purpose = "revocation",
                type = STATUS_LIST_2021
            )
        ).stream().map { arguments(it) }
    }
}