package id.walt.crypto2.hash

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class HashVectorSuiteTest {

    private val manifests = listOf(
        HashManifest(
            algorithm = HashAlgorithm.SHA_256,
            resourcePath = "hash/SHA256.json",
        ),
        HashManifest(
            algorithm = HashAlgorithm.SHA_384,
            resourcePath = "hash/SHA384.json",
        ),
        HashManifest(
            algorithm = HashAlgorithm.SHA_512,
            resourcePath = "hash/SHA512.json",
        ),
        HashManifest(
            algorithm = HashAlgorithm.SHA3_256,
            resourcePath = "hash/SHA3_256.json",
        ),
        HashManifest(
            algorithm = HashAlgorithm.SHA3_384,
            resourcePath = "hash/SHA3_384.json",
        ),
        HashManifest(
            algorithm = HashAlgorithm.SHA3_512,
            resourcePath = "hash/SHA3_512.json",
        ),
    )

    @Test
    fun `one shot hasher matches vector digests`() {
        manifests.forEach { manifest ->
            manifest.vectors.forEach { vector ->
                val hasher = HashFactory.create(manifest.algorithm)
                assertContentEquals(
                    expected = vector.digestBytes,
                    actual = hasher.hash(vector.messageBytes),
                    message = "Hasher from & to ByteArray mismatch for ${manifest.algorithm} (${vector.name})",
                )

                assertEquals(
                    expected = vector.digestByteString,
                    actual = hasher.hashToByteString(vector.messageBytes),
                    message = "Hasher from ByteArray to ByteString mismatch for ${manifest.algorithm} (${vector.name})",
                )

                assertEquals(
                    expected = vector.digestByteString,
                    actual = hasher.hashToByteString(vector.messageByteString),
                    message = "Hasher from & to ByteString mismatch for ${manifest.algorithm} (${vector.name})",
                )
            }
        }
    }

    @Test
    fun `stateful digest matches vectors across random chunking`() {
        manifests.forEachIndexed { index, manifest ->
            val rng = Random(index.toLong()) // deterministic per algorithm

            manifest.vectors.forEach { vector ->
                val digest = DigestFactory.create(manifest.algorithm)
                val message = vector.messageBytes

                var offset = 0
                while (offset < message.size) {
                    val remaining = message.size - offset
                    val chunkSize = rng.nextInt(1, remaining.coerceAtMost(65536) + 1)
                    val chunk = message.copyOfRange(offset, offset + chunkSize)
                    digest.update(chunk)
                    offset += chunkSize
                }

                assertContentEquals(
                    expected = vector.digestBytes,
                    actual = digest.finish(),
                    message = "Digest mismatch for ${manifest.algorithm} (${vector.name})",
                )

                // Ensure finish() reset works by re-using the instance.
                digest.update(message)
                assertContentEquals(
                    expected = vector.digestBytes,
                    actual = digest.finish(),
                    message = "Digest reuse mismatch for ${manifest.algorithm} (${vector.name})",
                )

                // Exercise ByteString update path.
                val byteStringDigest = DigestFactory.create(manifest.algorithm)
                val messageByteString = vector.messageByteString
                byteStringDigest.update(messageByteString)
                assertEquals(
                    expected = vector.digestByteString,
                    actual = byteStringDigest.finishToByteString(),
                    message = "Digest ByteString mismatch for ${manifest.algorithm} (${vector.name})",
                )

                // Verify reset() clears internal state even without calling finalize().
                val resetDigest = DigestFactory.create(manifest.algorithm)
                resetDigest.update(message)
                resetDigest.reset()
                resetDigest.update(message)
                assertContentEquals(
                    expected = vector.digestBytes,
                    actual = resetDigest.finish(),
                    message = "Digest reset mismatch for ${manifest.algorithm} (${vector.name})",
                )
            }
        }
    }

    @Test
    fun `async helpers behave like synchronous variants`() = runTest {
        manifests.forEach { manifest ->
            val vector = manifest.vectors.first()

            val hasher = HashFactory.create(manifest.algorithm)
            val digest = DigestFactory.create(manifest.algorithm)

            val asyncHash = hasher.hashAsync(vector.messageBytes)
            assertContentEquals(
                expected = vector.digestBytes,
                actual = asyncHash,
                message = "Async hasher mismatch for ${manifest.algorithm} (${vector.name})",
            )

            val asyncHashByteString = hasher.hashAsyncToByteString(vector.messageByteString)
            assertContentEquals(
                expected = vector.digestByteString.toByteArray(),
                actual = asyncHashByteString.toByteArray(),
                message = "Async hasher ByteString mismatch for ${manifest.algorithm} (${vector.name})",
            )

            val stringMessage = "async-${manifest.algorithm.name}-${vector.name}"
            val asyncHashFromString = hasher.hashAsync(stringMessage)
            val expectedFromString = hasher.hash(stringMessage.encodeToByteArray())
            assertContentEquals(expectedFromString, asyncHashFromString)

            val asyncHashFromStringByteString = hasher.hashAsyncToByteString(stringMessage)
            assertContentEquals(
                expectedFromString,
                asyncHashFromStringByteString.toByteArray(),
            )

            digest.update(vector.messageBytes)
            val asyncDigest = digest.finishAsync()
            assertContentEquals(
                expected = vector.digestBytes,
                actual = asyncDigest,
                message = "Async digest mismatch for ${manifest.algorithm} (${vector.name})",
            )

            val asyncDigestByteString = DigestFactory.create(manifest.algorithm).apply {
                update(vector.messageBytes)
            }.finishAsyncToByteString()
            assertEquals(
                expected = vector.digestByteString,
                actual = asyncDigestByteString,
                message = "Async digest ByteString mismatch for ${manifest.algorithm} (${vector.name})",
            )

            val asyncUpdateDigest = DigestFactory.create(manifest.algorithm)
            asyncUpdateDigest.updateAsync(vector.messageBytes)
            assertContentEquals(
                expected = vector.digestBytes,
                actual = asyncUpdateDigest.finish(),
                message = "Async update (bytes) mismatch for ${manifest.algorithm} (${vector.name})",
            )

            val asyncUpdateByteStringDigest = DigestFactory.create(manifest.algorithm)
            asyncUpdateByteStringDigest.updateAsync(vector.messageByteString)
            assertEquals(
                expected = vector.digestByteString,
                actual = asyncUpdateByteStringDigest.finishToByteString(),
                message = "Async update (ByteString) mismatch for ${manifest.algorithm} (${vector.name})",
            )

            val asyncUpdateStringDigest = DigestFactory.create(manifest.algorithm)
            asyncUpdateStringDigest.update(stringMessage)
            val expectedStringDigest = hasher.hash(stringMessage.encodeToByteArray())
            assertContentEquals(
                expectedStringDigest,
                asyncUpdateStringDigest.finish(),
            )

            val asyncStringDigest = DigestFactory.create(manifest.algorithm)
            asyncStringDigest.updateAsync(stringMessage)
            assertContentEquals(
                expectedStringDigest,
                asyncStringDigest.finish(),
                "Async update (String) mismatch for ${manifest.algorithm} (${vector.name})",
            )
        }
    }

    @Test
    fun `string hashing matches byte hashing`() {
        val messages = listOf(
            "",
            "hello, world",
            "pässwörd",
            "😀 unicode",
        )

        HashAlgorithm.entries.forEach { algorithm ->
            val hasher = HashFactory.create(algorithm)
            messages.forEach { message ->
                val expected = hasher.hash(message.encodeToByteArray())
                assertContentEquals(expected, hasher.hash(message))
                assertContentEquals(expected, hasher.hashToByteString(message).toByteArray())
            }
        }
    }

    @Test
    fun `mixed update paths produce expected digests`() {
        manifests.forEach { manifest ->
            val vector = manifest.vectors.first()

            val primaryBytes = vector.messageBytes
            val byteStringChunk: ByteString = vector.messageByteString
            val stringChunk = "mix-${manifest.algorithm.name}-${vector.name}"
            val asyncBytes = "async-bytes-${manifest.algorithm.name}".encodeToByteArray()
            val asyncByteStringChunk = "async-byteString-${vector.name}".encodeToByteArray().toByteString()
            val asyncStringChunk = "async-string-${manifest.algorithm.name}-${vector.name}"

            val expectedMessage = primaryBytes + byteStringChunk.toByteArray() +
                    stringChunk.encodeToByteArray() + asyncBytes + asyncByteStringChunk.toByteArray() +
                    asyncStringChunk.encodeToByteArray()

            val applySequence: (Digest) -> Unit = { digest ->
                digest.update(primaryBytes)
                digest.update(byteStringChunk)
                digest.update(stringChunk)
                runBlocking {
                    digest.updateAsync(asyncBytes)
                    digest.updateAsync(asyncByteStringChunk)
                    digest.updateAsync(asyncStringChunk)
                }
            }

            val digest = DigestFactory.create(manifest.algorithm).also(applySequence)
            val expectedDigest = HashFactory.create(manifest.algorithm).hash(expectedMessage)

            assertContentEquals(
                expectedDigest,
                digest.finish(),
                "Mixed update finish mismatch for ${manifest.algorithm}",
            )

            val byteStringDigest = DigestFactory.create(manifest.algorithm).also(applySequence)
            assertEquals(
                expectedDigest.toByteString(),
                byteStringDigest.finishToByteString(),
                "Mixed update finishToByteString mismatch for ${manifest.algorithm}",
            )
        }
    }

    @Test
    fun `reset handles edge scenarios`() {
        val emptyMessage = ByteArray(0)
        HashAlgorithm.entries.forEach { algorithm ->
            val message = "reset-${algorithm.name}".encodeToByteArray()
            val half = message.size / 2
            val expected = HashFactory.create(algorithm).hash(message)
            val expectedEmpty = HashFactory.create(algorithm).hash(emptyMessage)

            DigestFactory.create(algorithm).apply {
                assertContentEquals(expectedEmpty, finish(), "Empty digest mismatch for $algorithm")

                reset()
                update(message)
                assertContentEquals(expected, finish(), "Reset-before-use mismatch for $algorithm")

                update(message.copyOfRange(0, half))
                reset()
                update(message)
                assertContentEquals(expected, finish(), "Reset-after-partial mismatch for $algorithm")

                reset()
                reset()
                update(message)
                assertContentEquals(expected, finish(), "Repeated reset mismatch for $algorithm")
            }
        }
    }

}
