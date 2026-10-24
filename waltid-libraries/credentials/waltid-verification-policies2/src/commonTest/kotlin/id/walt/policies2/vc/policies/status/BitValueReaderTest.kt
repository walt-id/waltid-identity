package id.walt.policies2.vc.policies.status

import id.walt.policies2.vc.policies.status.bit.BigEndianRepresentation
import id.walt.policies2.vc.policies.status.bit.BitValueReader
import id.walt.policies2.vc.policies.status.expansion.BitstringStatusListExpansionAlgorithm
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BitValueReaderTest {
    @Test
    fun `reads multibit W3C values across byte boundaries`() = runTest {
        // Independent gzip fixture: 01 80 60 followed by 16,381 zero bytes.
        val encoded = "uH4sIAAAAAAAC_-3BMQEAAAgDINfUKEY3xh4gtwMAAAAAAAAAAAAAAAAAAAB0PUIaoPcAQAAA"
        val reader = BitValueReader(BigEndianRepresentation())
        val expansion = BitstringStatusListExpansionAlgorithm()

        for ((index, expected) in listOf(0uL to "000", 2uL to "011", 5uL to "001", 6uL to "100")) {
            assertEquals(
                expected.toList(),
                reader.get(encoded, index, bitSize = 3, expansionAlgorithm = expansion),
                "index=$index",
            )
        }
    }
}
