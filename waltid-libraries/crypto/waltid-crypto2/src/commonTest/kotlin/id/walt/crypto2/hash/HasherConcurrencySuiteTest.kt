package id.walt.crypto2.hash

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.ByteString
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class HasherConcurrencySuiteTest {

    private val messageString = "safety is an illusion concocted by humans"
    private val messageBytes = messageString.encodeToByteArray()
    private val messageByteString = messageBytes.toByteString()

    @Test
    fun `all hasher APIs produce consistent results under concurrent usage`() = runTest {
        HashAlgorithm.entries.forEach { algorithm ->
            val expectedBytes = HashFactory.create(algorithm).hash(messageBytes)
            val expectedByteString = expectedBytes.toByteString()

            verifyConcurrentBytes(expectedBytes) {
                HashFactory.create(algorithm).hash(messageBytes)
            }

            verifyConcurrentByteString(expectedByteString) {
                HashFactory.create(algorithm).hashToByteString(messageBytes)
            }

            verifyConcurrentBytes(expectedBytes) {
                HashFactory.create(algorithm).hash(messageByteString)
            }

            verifyConcurrentByteString(expectedByteString) {
                HashFactory.create(algorithm).hashToByteString(messageByteString)
            }

            verifyConcurrentBytes(expectedBytes) {
                HashFactory.create(algorithm).hash(messageString)
            }

            verifyConcurrentByteString(expectedByteString) {
                HashFactory.create(algorithm).hashToByteString(messageString)
            }

            verifyConcurrentBytes(expectedBytes) {
                HashFactory.create(algorithm).hashAsync(messageBytes)
            }

            verifyConcurrentByteString(expectedByteString) {
                HashFactory.create(algorithm).hashAsyncToByteString(messageByteString)
            }

            verifyConcurrentBytes(expectedBytes) {
                HashFactory.create(algorithm).hashAsync(messageString)
            }

            verifyConcurrentByteString(expectedByteString) {
                HashFactory.create(algorithm).hashAsyncToByteString(messageString)
            }
        }
    }

    @Test
    fun `all hasher APIs remain safe when sharing a single instance across coroutines`() = runTest {
        HashAlgorithm.entries.forEach { algorithm ->
            val shared = HashFactory.create(algorithm)
            val expectedBytes = shared.hash(messageBytes)
            val expectedByteString = expectedBytes.toByteString()

            verifyConcurrentBytes(expectedBytes) {
                shared.hash(messageBytes)
            }

            verifyConcurrentByteString(expectedByteString) {
                shared.hashToByteString(messageBytes)
            }

            verifyConcurrentBytes(expectedBytes) {
                shared.hash(messageByteString)
            }

            verifyConcurrentByteString(expectedByteString) {
                shared.hashToByteString(messageByteString)
            }

            verifyConcurrentBytes(expectedBytes) {
                shared.hash(messageString)
            }

            verifyConcurrentByteString(expectedByteString) {
                shared.hashToByteString(messageString)
            }

            verifyConcurrentBytes(expectedBytes) {
                shared.hashAsync(messageBytes)
            }

            verifyConcurrentByteString(expectedByteString) {
                shared.hashAsyncToByteString(messageByteString)
            }

            verifyConcurrentBytes(expectedBytes) {
                shared.hashAsync(messageString)
            }

            verifyConcurrentByteString(expectedByteString) {
                shared.hashAsyncToByteString(messageString)
            }
        }
    }

    private suspend fun verifyConcurrentBytes(expected: ByteArray, block: suspend () -> ByteArray) {
        withContext(Dispatchers.Default) {
            val results = List(CONCURRENCY_LEVEL) { async { block() } }.awaitAll()
            results.forEach { actual ->
                assertContentEquals(expected, actual)
            }
        }
    }

    private suspend fun verifyConcurrentByteString(expected: ByteString, block: suspend () -> ByteString) {
        withContext(Dispatchers.Default) {
            val results = List(CONCURRENCY_LEVEL) { async { block() } }.awaitAll()
            results.forEach { actual ->
                assertEquals(expected, actual)
            }
        }
    }

    companion object {
        private const val CONCURRENCY_LEVEL = 64
    }
}
