package id.walt.policies2.vc.status.validator

import id.walt.policies2.vc.policies.status.CredentialFetcher
import id.walt.policies2.vc.policies.status.Values.BITSTRING_STATUS_LIST
import id.walt.policies2.vc.policies.status.Values.BITSTRING_STATUS_LIST_ENTRY
import id.walt.policies2.vc.policies.status.Values.REVOCATION_LIST_2020
import id.walt.policies2.vc.policies.status.Values.REVOCATION_LIST_2020_ENTRY
import id.walt.policies2.vc.policies.status.Values.STATUS_LIST_2021
import id.walt.policies2.vc.policies.status.Values.STATUS_LIST_2021_ENTRY
import id.walt.policies2.vc.policies.status.Values.TOKEN_STATUS_LIST
import id.walt.policies2.vc.policies.status.bit.BitRepresentationStrategy
import id.walt.policies2.vc.policies.status.bit.BitValueReader
import id.walt.policies2.vc.policies.status.bit.BitValueReaderFactory
import id.walt.policies2.vc.policies.status.expansion.*
import id.walt.policies2.vc.policies.status.model.*
import id.walt.policies2.vc.policies.status.reader.StatusValueReader
import id.walt.policies2.vc.policies.status.validator.StatusValidator
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Named.named
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.reflect.KClass

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class StatusValidatorTestsBase<M : StatusEntry, K : StatusPolicyAttribute, T : StatusContent> {

    protected val uri = "https://example.com/status"
    protected val statusListContent = "jwt_content"
    protected val index = 1uL

    protected abstract fun getTestScenarios(): Stream<Arguments>

    protected abstract fun createStatusValidator(): StatusValidator<M, K>
    protected abstract fun createEntry(scenario: TestScenario, size: Int = 1): M
    protected abstract fun createAttribute(scenario: TestScenario, value: UInt): K
    protected abstract fun createStatusContent(scenario: TestScenario, size: Int): T
    protected abstract fun getBitRepresentationStrategy(): BitRepresentationStrategy
    protected abstract fun setupBitValueReader(statusSize: Int, scenario: TestScenario, bitValue: List<Char>)
    protected abstract fun verifyBitValueReaderInteraction(
        statusSize: Int, expansionAlgorithmType: KClass<out StatusListExpansionAlgorithm>
    )

    protected val mockFetcher: CredentialFetcher = mockk()
    protected val mockStatusReader: StatusValueReader<T> = mockk()
    protected val mockBitValueReaderFactory: BitValueReaderFactory = mockk()
    protected val mockBitValueReader: BitValueReader = mockk()
    protected val mockStatusListExpansionAlgorithmFactory: StatusListExpansionAlgorithmFactory<T> = mockk()
    protected val mockExpansionAlgorithm: StatusListExpansionAlgorithm = mockk()

    protected lateinit var sut: StatusValidator<M, K>

    @Nested
    @DisplayName("Successful validation scenarios")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class SuccessfulValidationScenarios {

        // Use instance method that delegates to the abstract function
        fun getTestScenarios() = this@StatusValidatorTestsBase.getTestScenarios()

        @ParameterizedTest
        @MethodSource("getTestScenarios")
        @DisplayName("Should successfully validate multi-bit values")
        fun shouldValidateMultiBitValues(scenario: TestScenario) = runTest {
            testSuccessfulValidation(scenario, statusSize = 3, bitValue = listOf('1', '0', '1'), expectedValue = 5u)
        }

        @ParameterizedTest
        @MethodSource("getTestScenarios")
        @DisplayName("Should successfully validate single bit values")
        fun shouldValidateSingleBitValues(scenario: TestScenario) = runTest {
            testSuccessfulValidation(scenario, statusSize = 1, bitValue = listOf('1'), expectedValue = 1u)
        }

        @ParameterizedTest
        @MethodSource("getTestScenarios")
        @DisplayName("Should successfully validate zero values")
        fun shouldValidateZeroValues(scenario: TestScenario) = runTest {
            testSuccessfulValidation(scenario, statusSize = 1, bitValue = listOf('0'), expectedValue = 0u)
        }
    }

    @Nested
    @DisplayName("Fetcher error scenarios")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class FetcherErrorScenarios {

        fun getTestScenarios() = this@StatusValidatorTestsBase.getTestScenarios()

        @ParameterizedTest
        @MethodSource("getTestScenarios")
        @DisplayName("Should throw StatusRetrievalError when fetcher fails")
        fun shouldHandleFetcherErrors(scenario: TestScenario) = runTest {
            val entry = createEntry(scenario, size = 1)
            val attribute = createAttribute(scenario, 5u)
            val error = RuntimeException("Network error")

            coEvery { mockFetcher.fetch(uri) } returns Result.failure(error)

            val exception = assertThrows<StatusRetrievalError> { sut.validate(entry, attribute).getOrThrow() }
            assertTrue(exception.message.contains("Network error"))
        }

        @ParameterizedTest
        @MethodSource("getTestScenarios")
        @DisplayName("Should handle empty error messages gracefully")
        fun shouldHandleEmptyMessages(scenario: TestScenario) = runTest {
            val entry = createEntry(scenario, size = 1)
            val attribute = createAttribute(scenario, 5u)

            coEvery { mockFetcher.fetch(uri) } returns Result.failure(RuntimeException())

            val exception = assertThrows<StatusRetrievalError> { sut.validate(entry, attribute).getOrThrow() }
            assertEquals("Status credential download error", exception.message)
        }
    }

    @Nested
    @DisplayName("Reader error scenarios")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class ReaderErrorScenarios {

        fun getTestScenarios() = this@StatusValidatorTestsBase.getTestScenarios()

        @ParameterizedTest
        @MethodSource("getTestScenarios")
        @DisplayName("Should throw StatusRetrievalError when reader fails")
        fun shouldHandleReaderErrors(scenario: TestScenario) = runTest {
            val entry = createEntry(scenario, size = 1)
            val attribute = createAttribute(scenario, 5u)
            val error = RuntimeException("JWT parsing error")

            coEvery { mockFetcher.fetch(uri) } returns Result.success(statusListContent)
            every { mockStatusReader.read(statusListContent) } returns Result.failure(error)

            val exception = assertThrows<StatusRetrievalError> { sut.validate(entry, attribute).getOrThrow() }
            assertTrue(exception.message.contains("JWT parsing error"))
        }

        @ParameterizedTest
        @MethodSource("getTestScenarios")
        @DisplayName("Should handle empty reader error messages gracefully")
        fun shouldHandleEmptyReaderMessages(scenario: TestScenario) = runTest {
            val entry = createEntry(scenario, size = 1)
            val attribute = createAttribute(scenario, 5u)

            coEvery { mockFetcher.fetch(uri) } returns Result.success(statusListContent)
            every { mockStatusReader.read(statusListContent) } returns Result.failure(RuntimeException())

            val exception = assertThrows<StatusRetrievalError> { sut.validate(entry, attribute).getOrThrow() }
            assertEquals("Status credential parsing error", exception.message)
        }
    }

    @Nested
    @DisplayName("Status validation error scenarios")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class StatusValidationErrorScenarios {

        fun getTestScenarios() = this@StatusValidatorTestsBase.getTestScenarios()

        @ParameterizedTest
        @MethodSource("getTestScenarios")
        @DisplayName("Should throw StatusVerificationError for empty bit values")
        fun shouldRejectEmptyBitValues(scenario: TestScenario) = runTest {
            testValidationError(scenario, emptyList(), 1, "Null or empty bit value")
        }

        @ParameterizedTest
        @MethodSource("getTestScenarios")
        @DisplayName("Should throw StatusVerificationError for invalid bit characters")
        fun shouldRejectInvalidBitCharacters(scenario: TestScenario) = runTest {
            testValidationError(scenario, listOf('1', '0', '2'), 3, "Invalid bit value: [1, 0, 2]")
        }

        @ParameterizedTest
        @MethodSource("getTestScenarios")
        @DisplayName("Should throw StatusVerificationError for letter characters")
        fun shouldRejectLetterCharacters(scenario: TestScenario) = runTest {
            testValidationError(scenario, listOf('1', 'a', '0'), 3, "Invalid bit value: [1, a, 0]")
        }

        @ParameterizedTest
        @MethodSource("getTestScenarios")
        @DisplayName("Should throw StatusVerificationError when values don't match")
        fun shouldRejectValueMismatch(scenario: TestScenario) = runTest {
            val entry = createEntry(scenario, size = 3)
            val attribute = createAttribute(scenario, 7u) // expecting 7 but getting 5
            val content = createStatusContent(scenario, size = 3)

            setupSuccessfulFetchAndRead(content, scenario.expansionAlgorithmType)
            setupBitValueReader(3, scenario, listOf('1', '0', '1')) // binary 101 = 5

            val exception = assertThrows<StatusVerificationError> { sut.validate(entry, attribute).getOrThrow() }
            assertEquals("Status validation failed: expected 7, but got 5", exception.message)
        }
    }

    // Protected helper methods for subclasses
    protected suspend fun testSuccessfulValidation(
        scenario: TestScenario,
        statusSize: Int,
        bitValue: List<Char>,
        expectedValue: UInt
    ) {
        val entry = createEntry(scenario, size = statusSize)
        val attribute = createAttribute(scenario, expectedValue)
        val content = createStatusContent(scenario, size = statusSize)

        setupValidationScenario(scenario, content, bitValue, statusSize)

        val result = sut.validate(entry, attribute)
        assertTrue(result.isSuccess)
        verifyAllInteractions(statusSize, scenario.expansionAlgorithmType)
    }

    protected suspend fun testValidationError(
        scenario: TestScenario,
        bitValue: List<Char>,
        statusSize: Int,
        expectedMessage: String
    ) {
        val entry = createEntry(scenario, size = statusSize)
        val attribute = createAttribute(scenario, 5u)
        val content = createStatusContent(scenario, size = statusSize)

        setupValidationScenario(scenario, content, bitValue, statusSize)

        val exception = assertThrows<StatusVerificationError> { sut.validate(entry, attribute).getOrThrow() }
        assertTrue(exception.message.contains(expectedMessage))
    }

    protected fun setupValidationScenario(scenario: TestScenario, content: T, bitValue: List<Char>, statusSize: Int) {
        setupSuccessfulFetchAndRead(content, scenario.expansionAlgorithmType)
        setupBitValueReader(statusSize, scenario, bitValue)
    }

    protected fun setupSuccessfulFetchAndRead(content: T, expansionAlgorithmType: KClass<out StatusListExpansionAlgorithm>) {
        coEvery { mockFetcher.fetch(uri) } returns Result.success(statusListContent)
        every { mockStatusReader.read(statusListContent) } returns Result.success(content)
        every { mockStatusListExpansionAlgorithmFactory.create(content) } returns mockkClass(expansionAlgorithmType)
    }

    protected fun verifyAllInteractions(
        statusSize: Int,
        expansionAlgorithmType: KClass<out StatusListExpansionAlgorithm>
    ) {
        coVerify { mockFetcher.fetch(uri) }
        verify { mockStatusReader.read(statusListContent) }
        verify { mockBitValueReaderFactory.new(strategy = match { it::class == getBitRepresentationStrategy()::class }) }
        verifyBitValueReaderInteraction(statusSize, expansionAlgorithmType)
    }

    data class TestScenario(
        val statusType: String,
        val entryType: String? = null,
        val expansionAlgorithmType: KClass<out StatusListExpansionAlgorithm>
    )

    companion object {
        @JvmStatic
        fun w3cTestScenarios() = Stream.of(
            arguments(
                named(
                    REVOCATION_LIST_2020, TestScenario(
                        statusType = REVOCATION_LIST_2020,
                        entryType = REVOCATION_LIST_2020_ENTRY,
                        expansionAlgorithmType = RevocationList2020ExpansionAlgorithm::class
                    )
                )
            ),
            arguments(
                named(
                    STATUS_LIST_2021, TestScenario(
                        statusType = STATUS_LIST_2021,
                        entryType = STATUS_LIST_2021_ENTRY,
                        expansionAlgorithmType = StatusList2021ExpansionAlgorithm::class
                    )
                )
            ),
            arguments(
                named(
                    BITSTRING_STATUS_LIST, TestScenario(
                        statusType = BITSTRING_STATUS_LIST,
                        entryType = BITSTRING_STATUS_LIST_ENTRY,
                        expansionAlgorithmType = BitstringStatusListExpansionAlgorithm::class
                    )
                )
            ),
        )

        @JvmStatic
        fun ietfTestScenarios() = Stream.of(
            arguments(
                named(
                    TOKEN_STATUS_LIST, TestScenario(
                        statusType = TOKEN_STATUS_LIST,
                        expansionAlgorithmType = TokenStatusListExpansionAlgorithm::class
                    )
                )
            )
        )
    }
}
