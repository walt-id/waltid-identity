package id.walt.policies.policies.status.validator

import id.walt.policies.policies.status.bit.BitRepresentationStrategy
import id.walt.policies.policies.status.bit.LittleEndianRepresentation
import id.walt.policies.policies.status.expansion.StatusListExpansionAlgorithm
import id.walt.policies.policies.status.model.IETFEntry
import id.walt.policies.policies.status.model.IETFStatusContent
import id.walt.policies.policies.status.model.IETFStatusPolicyAttribute
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.provider.Arguments
import java.util.stream.Stream
import kotlin.reflect.KClass

@DisplayName("IETFStatusValidator Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IETFStatusValidatorTests : StatusValidatorTestsBase<IETFEntry, IETFStatusPolicyAttribute, IETFStatusContent>() {

    @BeforeEach
    fun setup() {
        coEvery { mockFetcher.fetch(uri) } returns Result.success(statusListContent)
        every { mockBitValueReaderFactory.new(strategy = ofType(LittleEndianRepresentation::class)) } returns mockBitValueReader
        sut = createStatusValidator()
    }

    override fun getTestScenarios(): Stream<Arguments> = ietfTestScenarios()

    override fun createStatusValidator() = IETFStatusValidator(
        fetcher = mockFetcher,
        reader = mockStatusReader,
        bitValueReaderFactory = mockBitValueReaderFactory,
        expansionAlgorithm = mockExpansionAlgorithm,
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
        every {
            mockBitValueReader.get(any(), index, statusSize, mockExpansionAlgorithm)
        } returns bitValue
    }

    override fun verifyBitValueReaderInteraction(
        statusSize: Int, expansionAlgorithmType: KClass<out StatusListExpansionAlgorithm>
    ) {
        verify { mockBitValueReader.get(any(), index, statusSize, mockExpansionAlgorithm) }
    }
}