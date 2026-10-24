package id.walt.verifier.openid.transactiondata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TransactionDataHashingTest {
    @Test
    fun `calculates sha-256 hashes over encoded transaction_data values`() {
        val encoded = TransactionDataTestFixtures.transactionData(hashAlgorithms = listOf("sha-256"))
        val differentlyFormatted = "ewogInR5cGUiOiAib3JnLndhbHRpZC50cmFuc2FjdGlvbi1kYXRhLnBheW1lbnQtYXV0aG9yaXphdGlvbiIsCiAiY3JlZGVudGlhbF9pZHMiOiBbCiAgInBheW1lbnRfY3JlZGVudGlhbCIKIF0sCiAidHJhbnNhY3Rpb25fZGF0YV9oYXNoZXNfYWxnIjogWwogICJzaGEtMjU2IgogXSwKICJhbW91bnQiOiAiNDIuMDAiLAogInBheWVlIjogIkFDTUUgQ29ycCIsCiAiY3VycmVuY3kiOiAiRVVSIgp9"

        val hashes = calculateTransactionDataHashes(listOf(encoded, differentlyFormatted))

        // OID4VP B.3.3.1 hashes the received strings, preserving even JSON formatting differences.
        assertEquals(
            listOf("FQRrx7n-hnj_AbO_2-jBufe80bxdchqb5en1WlFBIgU", "nEqrWGZyHX6ZyvHF1W0sKk9h92-FrpgboRgbEKK9inY"),
            hashes,
        )
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
