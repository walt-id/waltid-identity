package id.walt.crypto2.hash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FactoryMetadataTest {

    @Test
    fun `hash factory exposes algorithm metadata`() {
        HashAlgorithm.entries.forEach { algorithm ->
            val hasher = HashFactory.create(algorithm)
            assertEquals(algorithm, hasher.algorithm, "Hasher returned mismatched algorithm for ${algorithm.name}")
        }
    }

    @Test
    fun `digest factory exposes algorithm metadata`() {
        HashAlgorithm.entries.forEach { algorithm ->
            val digest = DigestFactory.create(algorithm)
            assertEquals(algorithm, digest.algorithm, "Digest returned mismatched algorithm for ${algorithm.name}")
        }
    }

    @Test
    fun `hash algorithms expose unique names`() {
        val names = HashAlgorithm.entries.map { it.name }
        assertEquals(names.size, names.toSet().size, "Duplicate hash algorithm names detected")
        assertTrue(names.all { it.isNotBlank() }, "Hash algorithm names must not be blank")
    }
}
