package id.walt.verifier.openid.transactiondata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MdocTransactionDataConventionTest {
    @Test
    fun `builds and parses mdoc transaction_data keys`() {
        val key = deviceSignedItemKey(2)

        assertEquals("transaction_data_2", key)
        assertEquals(2, parseDeviceSignedItemIndex(key))
        assertNull(parseDeviceSignedItemIndex("other_2"))
    }

    @Test
    fun `creates expected key set for number of transaction_data items`() {
        assertEquals(
            setOf("transaction_data_0", "transaction_data_1", "transaction_data_2"),
            deviceSignedItemKeys(3),
        )
    }

    @Test
    fun `requires contiguous indices from zero`() {
        requireContiguousIndices(listOf(2, 1, 0))

        assertFailsWith<IllegalArgumentException> {
            requireContiguousIndices(listOf(0, 2))
        }
    }
}
