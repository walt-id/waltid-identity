package id.walt.verifier.openid.transactiondata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TransactionDataHashingTest {
    @Test
    fun `calculates sha-256 hashes over encoded transaction_data values`() {
        val encoded = TransactionDataTestFixtures.transactionData(hashAlgorithms = listOf("sha-256"))

        val hashes = calculateTransactionDataHashes(listOf(encoded))

        assertEquals(1, hashes.size)
    }

    @Test
    fun `resolveHashAlgorithm returns null when no transaction_data is present`() {
        assertNull(resolveHashAlgorithm(emptyList()))
    }

    @Test
    fun `resolveHashAlgorithm returns default when supported request algorithms are present`() {
        val encoded = TransactionDataTestFixtures.transactionData(hashAlgorithms = listOf("sha-256"))
        val decoded = decodeList(listOf(encoded))

        val algorithm = resolveHashAlgorithm(decoded)

        assertEquals(DEFAULT_HASH_ALGORITHM, algorithm)
    }

    @Test
    fun `resolveHashAlgorithm rejects unsupported request algorithms`() {
        val encoded = TransactionDataTestFixtures.transactionData(hashAlgorithms = listOf("sha-512"))
        val decoded = decodeList(listOf(encoded))

        assertFailsWith<IllegalArgumentException> {
            resolveHashAlgorithm(decoded)
        }
    }
}
