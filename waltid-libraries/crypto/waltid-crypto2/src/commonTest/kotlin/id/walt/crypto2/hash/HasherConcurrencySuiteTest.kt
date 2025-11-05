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

    @Test
    fun `cross algorithm hashing remains isolated`() = runTest {
        val messages = HashAlgorithm.entries.associateWith { algorithm ->
            "mix-${algorithm.name}-${messageString}".encodeToByteArray()
        }
        val expectedBytes = messages.mapValues { (algorithm, payload) ->
            HashFactory.create(algorithm).hash(payload)
        }
        val expectedByteStrings = expectedBytes.mapValues { (_, bytes) -> bytes.toByteString() }
        val shared = HashAlgorithm.entries.associateWith { HashFactory.create(it) }

        val byteResults = withContext(Dispatchers.Default) {
            List(CONCURRENCY_LEVEL * HashAlgorithm.entries.size) { index ->
                async {
                    val algorithm = HashAlgorithm.entries[index % HashAlgorithm.entries.size]
                    val message = messages.getValue(algorithm)
                    val hasher = shared.getValue(algorithm)
                    algorithm to hasher.hash(message)
                }
            }.awaitAll()
        }

        byteResults.forEach { (algorithm, actual) ->
            assertContentEquals(
                expectedBytes.getValue(algorithm),
                actual,
                "Hasher cross-algorithm byte mismatch for ${algorithm.name}",
            )
        }

        val byteStringResults = withContext(Dispatchers.Default) {
            List(CONCURRENCY_LEVEL * HashAlgorithm.entries.size) { index ->
                async {
                    val algorithm = HashAlgorithm.entries[index % HashAlgorithm.entries.size]
                    val message = messages.getValue(algorithm).toByteString()
                    val hasher = shared.getValue(algorithm)
                    algorithm to hasher.hashToByteString(message)
                }
            }.awaitAll()
        }

        byteStringResults.forEach { (algorithm, actual) ->
            assertEquals(
                expectedByteStrings.getValue(algorithm),
                actual,
                "Hasher cross-algorithm ByteString mismatch for ${algorithm.name}",
            )
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
