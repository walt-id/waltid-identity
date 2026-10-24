package id.walt.policies.policies.status.validator

import id.walt.policies.policies.Base64UrlHandler
import id.walt.policies.policies.status.bit.BitRepresentationStrategy
import id.walt.policies.policies.status.bit.BitValueReaderFactory
import id.walt.policies.policies.status.bit.LittleEndianRepresentation
import id.walt.policies.policies.status.expansion.StatusListExpansionAlgorithm
import id.walt.policies.policies.status.expansion.TokenStatusListExpansionAlgorithm
import id.walt.policies.policies.status.model.IETFEntry
import id.walt.policies.policies.status.model.IETFStatusContent
import id.walt.policies.policies.status.model.IETFStatusPolicyAttribute
import id.walt.policies.policies.status.model.StatusVerificationError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.provider.Arguments
import java.util.stream.Stream
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@DisplayName("IETFStatusValidator Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IETFStatusValidatorTests : StatusValidatorTestsBase<IETFEntry, IETFStatusPolicyAttribute, IETFStatusContent>() {

    @BeforeEach
    fun setup() {
        coEvery { mockFetcher.fetch(uri) } returns Result.success(statusListContent)
        every { mockBitValueReaderFactory.new(strategy = ofType(LittleEndianRepresentation::class)) } returns mockBitValueReader
        every { mockStatusReader.canHandle(statusListContent) } returns true
        sut = createStatusValidator()
    }

    @Test
    fun `validates independently encoded two-bit status values`() = runTest {
        // draft-ietf-oauth-status-list-20 sections 4.1 and 4.2: C9 44 F9.
        every { mockStatusReader.read(statusListContent) } returns
            Result.success(IETFStatusContent(list = "eNo76fITAAPfAgc", size = 2))
        val validator = IETFStatusValidator(
            fetcher = mockFetcher,
            bitValueReaderFactory = BitValueReaderFactory(),
            expansionAlgorithm = TokenStatusListExpansionAlgorithm(Base64UrlHandler()),
            mockStatusReader,
        )

        listOf(1u, 2u, 0u, 3u, 0u, 1u, 0u, 1u, 1u, 2u, 3u, 3u).forEachIndexed { index, expected ->
            val entry = IETFEntry(IETFEntry.StatusListField(index = index.toULong(), uri = uri))
            validator.validate(entry, IETFStatusPolicyAttribute(value = expected)).getOrThrow()
        }

        val first = IETFEntry(IETFEntry.StatusListField(index = 0uL, uri = uri))
        val mismatch = validator.validate(first, IETFStatusPolicyAttribute(value = 2u)).exceptionOrNull()
        assertIs<StatusVerificationError>(mismatch)
        assertEquals("Status validation failed: expected 2, but got 1", mismatch.message)
    }

    override fun getTestScenarios(): Stream<Arguments> = ietfTestScenarios()

    override fun createStatusValidator() = IETFStatusValidator(
        fetcher = mockFetcher,
        bitValueReaderFactory = mockBitValueReaderFactory,
        expansionAlgorithm = mockExpansionAlgorithm,
        mockStatusReader,
    )

    override fun createEntry(scenario: TestScenario, size: Int) = IETFEntry(
        statusList = IETFEntry.StatusListField(
            index = index,
            uri = uri
        )
    )

    override fun createAttribute(scenario: TestScenario, value: UInt) = IETFStatusPolicyAttribute(
        value = value
    )

    override fun createStatusContent(scenario: TestScenario, size: Int) = IETFStatusContent(
        list = "encoded_ietf_list_default",
        size = size
    )

    override fun getBitRepresentationStrategy(): BitRepresentationStrategy = LittleEndianRepresentation()

    override fun setupBitValueReader(statusSize: Int, scenario: TestScenario, bitValue: List<Char>) {
        coEvery {
            mockBitValueReader.get(any(), index, statusSize, mockExpansionAlgorithm)
        } returns bitValue
    }

    override fun verifyBitValueReaderInteraction(
        statusSize: Int, expansionAlgorithmType: KClass<out StatusListExpansionAlgorithm>
    ) {
        coVerify { mockBitValueReader.get(any(), index, statusSize, mockExpansionAlgorithm) }
    }
}
