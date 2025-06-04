package id.walt.policies.policies.status.validator

import id.walt.policies.policies.status.model.W3CStatusContent
import id.walt.policies.policies.status.model.W3CStatusPolicyAttribute
import id.walt.policies.policies.status.bit.BigEndianRepresentation
import id.walt.policies.policies.status.bit.BitRepresentationStrategy
import id.walt.policies.policies.status.model.W3CEntry
import id.walt.policies.policies.status.model.StatusVerificationError
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@DisplayName("W3CStatusValidator Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class W3CStatusValidatorTests : StatusValidatorTestsBase<W3CEntry, W3CStatusPolicyAttribute, W3CStatusContent>() {

    @BeforeEach
    fun setup() {
        coEvery { mockFetcher.fetch(uri) } returns Result.success(statusListContent)
        every { mockBitValueReaderFactory.new(strategy = ofType(BigEndianRepresentation::class)) } returns mockBitValueReader
        sut = createStatusValidator()
    }

    override fun getTestScenarios(): Stream<Arguments> = w3cTestScenarios()

    override fun createStatusValidator() = W3CStatusValidator(
        fetcher = mockFetcher,
        reader = mockStatusReader,
        bitValueReaderFactory = mockBitValueReaderFactory,
    )

    override fun createEntry(scenario: TestScenario, size: Int) = W3CEntry(
        id = "https://example.com/status#id",
        type = scenario.entryType!!,
        purpose = "revocation",
        index = index,
        size = size,
        uri = uri
    )

    override fun createAttribute(scenario: TestScenario, value: UInt) = W3CStatusPolicyAttribute(
        value = value,
        purpose = "revocation",
        type = scenario.statusType
    )

    override fun createStatusContent(scenario: TestScenario, size: Int) = W3CStatusContent(
        type = scenario.statusType,
        purpose = "revocation",
        size = size,
        list = "encoded_list"
    )

    override fun getBitRepresentationStrategy(): BitRepresentationStrategy = BigEndianRepresentation()

    override fun getContentSize(content: W3CStatusContent): Int = content.size

    @Nested
    @DisplayName("W3C-specific validation error scenarios")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class W3CSpecificValidationErrorScenarios {

        @ParameterizedTest
        @MethodSource("id.walt.policies.policies.status.validator.StatusValidatorTestsBase#w3cTestScenarios")
        @DisplayName("Should throw StatusVerificationError when purpose doesn't match")
        fun shouldRejectPurposeMismatch(scenario: TestScenario) = runTest {
            val entry = createEntry(scenario, size = 3)
            val attribute = createW3CAttribute(scenario.statusType, 5u, purpose = "wrong-purpose")
            val content = createStatusContent(scenario, size = 3)

            setupValidationScenario(scenario, content, listOf('1', '0', '1'))

            val exception = assertThrows<StatusVerificationError> { sut.validate(entry, attribute).getOrThrow() }
            assertEquals(
                "Purpose validation failed: expected wrong-purpose, but got revocation",
                exception.message
            )
        }

        @ParameterizedTest
        @MethodSource("id.walt.policies.policies.status.validator.StatusValidatorTestsBase#w3cTestScenarios")
        @DisplayName("Should throw StatusVerificationError when type doesn't match")
        fun shouldRejectTypeMismatch(scenario: TestScenario) = runTest {
            val entry = createEntry(scenario, size = 3)
            val attribute = createW3CAttribute("wrong-type", 5u)
            val content = createStatusContent(scenario, size = 3)

            setupValidationScenario(scenario, content, listOf('1', '0', '1'))

            val exception = assertThrows<StatusVerificationError> { sut.validate(entry, attribute).getOrThrow() }
            assertEquals(
                "Type validation failed: expected wrong-type, but got ${scenario.statusType}",
                exception.message
            )
        }
    }

    // W3C-specific helper methods for custom validation scenarios
    private fun createW3CAttribute(
        type: String,
        value: UInt,
        purpose: String = "revocation"
    ) = W3CStatusPolicyAttribute(
        value = value,
        purpose = purpose,
        type = type
    )
}