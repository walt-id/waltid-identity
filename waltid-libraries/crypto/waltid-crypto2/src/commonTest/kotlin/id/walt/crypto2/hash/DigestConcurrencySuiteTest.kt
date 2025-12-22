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

class DigestConcurrencySuiteTest {

    private val messageString = "entropy prefers the courageous"
    private val messageBytes = messageString.encodeToByteArray()
    private val messageByteString = messageBytes.toByteString()
    private val messageChunks = listOf(
        messageBytes.copyOfRange(0, messageBytes.size / 2),
        messageBytes.copyOfRange(messageBytes.size / 2, messageBytes.size),
    )

    @Test
    fun `all digest APIs produce consistent results under concurrent usage`() = runTest {
        HashAlgorithm.entries.forEach { algorithm ->
            val expectedBytes = HashFactory.create(algorithm).hash(messageBytes)
            val expectedByteString = expectedBytes.toByteString()

            verifyConcurrentBytes(expectedBytes) {
                DigestFactory.create(algorithm).apply {
                    messageChunks.forEach { update(it) }
                }.finish()
            }

            verifyConcurrentByteString(expectedByteString) {
                DigestFactory.create(algorithm).apply {
                    update(messageBytes)
                }.finishToByteString()
            }

            verifyConcurrentBytes(expectedBytes) {
                DigestFactory.create(algorithm).apply {
                    update(messageByteString)
                }.finish()
            }

            verifyConcurrentByteString(expectedByteString) {
                DigestFactory.create(algorithm).apply {
                    update(messageByteString)
                }.finishToByteString()
            }

            verifyConcurrentBytes(expectedBytes) {
                DigestFactory.create(algorithm).apply {
                    update(messageString)
                }.finish()
            }

            verifyConcurrentByteString(expectedByteString) {
                DigestFactory.create(algorithm).apply {
                    update(messageString)
                }.finishToByteString()
            }

            verifyConcurrentBytes(expectedBytes) {
                DigestFactory.create(algorithm).apply {
                    updateAsync(messageBytes)
                }.finish()
            }

            verifyConcurrentBytes(expectedBytes) {
                DigestFactory.create(algorithm).apply {
                    updateAsync(messageByteString)
                }.finish()
            }

            verifyConcurrentBytes(expectedBytes) {
                DigestFactory.create(algorithm).apply {
                    updateAsync(messageString)
                }.finish()
            }

            verifyConcurrentBytes(expectedBytes) {
                DigestFactory.create(algorithm).apply {
                    update(messageBytes)
                }.finishAsync()
            }

            verifyConcurrentByteString(expectedByteString) {
                DigestFactory.create(algorithm).apply {
                    update(messageBytes)
                }.finishAsyncToByteString()
            }

            verifyConcurrentBytes(expectedBytes) {
                DigestFactory.create(algorithm).apply {
                    update(messageBytes)
                    finish()
                    reset()
                    update(messageBytes)
                }.finish()
            }
        }
    }

    @Test
    fun `all digest APIs remain consistent when sharing a single instance across coroutines`() = runTest {
        HashAlgorithm.entries.forEach { algorithm ->
            val expectedBytes = HashFactory.create(algorithm).hash(messageBytes)
            val expectedByteString = expectedBytes.toByteString()

            val shared = DigestFactory.create(algorithm)

            verifyConcurrentBytes(expectedBytes) {
                shared.reset()
                messageChunks.forEach { shared.update(it) }
                shared.finish()
            }

            verifyConcurrentByteString(expectedByteString) {
                shared.reset()
                shared.update(messageBytes)
                shared.finishToByteString()
            }

            verifyConcurrentBytes(expectedBytes) {
                shared.reset()
                shared.update(messageByteString)
                shared.finish()
            }

            verifyConcurrentByteString(expectedByteString) {
                shared.reset()
                shared.update(messageByteString)
                shared.finishToByteString()
            }

            verifyConcurrentBytes(expectedBytes) {
                shared.reset()
                shared.update(messageString)
                shared.finish()
            }

            verifyConcurrentByteString(expectedByteString) {
                shared.reset()
                shared.update(messageString)
                shared.finishToByteString()
            }

            verifyConcurrentBytes(expectedBytes) {
                shared.reset()
                shared.updateAsync(messageBytes)
                shared.finish()
            }

            verifyConcurrentBytes(expectedBytes) {
                shared.reset()
                shared.updateAsync(messageByteString)
                shared.finish()
            }

            verifyConcurrentBytes(expectedBytes) {
                shared.reset()
                shared.updateAsync(messageString)
                shared.finish()
            }

            verifyConcurrentBytes(expectedBytes) {
                shared.reset()
                shared.update(messageBytes)
                shared.finishAsync()
            }

            verifyConcurrentByteString(expectedByteString) {
                shared.reset()
                shared.update(messageBytes)
                shared.finishAsyncToByteString()
            }
        }
    }

    @Test
    fun `concurrent digests across algorithms remain isolated`() = runTest {
        val messages = HashAlgorithm.entries.associateWith { algorithm ->
            "mix-${algorithm.name}-${messageString}".encodeToByteArray()
        }
        val expected = messages.mapValues { (algorithm, payload) ->
            HashFactory.create(algorithm).hash(payload)
        }

        val results = withContext(Dispatchers.Default) {
            List(CONCURRENCY_LEVEL * HashAlgorithm.entries.size) { index ->
                async {
                    val algorithm = HashAlgorithm.entries[index % HashAlgorithm.entries.size]
                    val digest = DigestFactory.create(algorithm)
                    val payload = messages.getValue(algorithm)
                    digest.update(payload)
                    algorithm to digest.finish()
                }
            }.awaitAll()
        }

        results.forEach { (algorithm, actual) ->
            assertContentEquals(
                expected.getValue(algorithm),
                actual,
                "Digest cross-algorithm mismatch for ${algorithm.name}",
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
