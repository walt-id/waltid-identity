package id.walt.crypto2.hash

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class HashVectorSuiteTest {

    private val manifests = listOf(
        HashManifest(
            algorithm = HashAlgorithm.SHA256,
            resourcePath = "hash/SHA256.json"
        ),
        HashManifest(
            algorithm = HashAlgorithm.SHA384,
            resourcePath = "hash/SHA384.json"
        ),
        HashManifest(
            algorithm = HashAlgorithm.SHA512,
            resourcePath = "hash/SHA512.json"
        ),
        HashManifest(
            algorithm = HashAlgorithm.SHA3_256,
            resourcePath = "hash/SHA3_256.json"
        ),
        HashManifest(
            algorithm = HashAlgorithm.SHA3_384,
            resourcePath = "hash/SHA3_384.json"
        ),
        HashManifest(
            algorithm = HashAlgorithm.SHA3_512,
            resourcePath = "hash/SHA3_512.json"
        ),
    )

    @Test
    fun `one shot hasher matches vector digests`() {
        manifests.forEach { manifest ->
            manifest.vectors.forEach { vector ->
                val hasher = manifest.algorithm.createHasher()
                assertContentEquals(
                    expected = vector.digestBytes,
                    actual = hasher.hash(vector.messageBytes),
                    message = "Hasher mismatch for ${manifest.algorithm} (${vector.name})",
                )

                // Verify ByteString overload mirrors ByteArray behaviour
                val byteStringResult = hasher.hash(vector.messageByteString)
                assertContentEquals(
                    expected = vector.digestByteString.toByteArray(),
                    actual = byteStringResult.toByteArray(),
                    message = "Hasher ByteString mismatch for ${manifest.algorithm} (${vector.name})",
                )
            }
        }
    }

    @Test
    fun `stateful digest matches vectors across random chunking`() {
        manifests.forEachIndexed { index, manifest ->
            val rng = Random(index.toLong()) // deterministic per algorithm

            manifest.vectors.forEach { vector ->
                val digest = manifest.algorithm.createDigest()
                val message = vector.messageBytes

                var offset = 0
                while (offset < message.size) {
                    val remaining = message.size - offset
                    val chunkSize = rng.nextInt(1, remaining.coerceAtMost(65536) + 1)
                    digest.update(message, offset, chunkSize)
                    offset += chunkSize
                }

                assertContentEquals(
                    expected = vector.digestBytes,
                    actual = digest.digest(),
                    message = "Digest mismatch for ${manifest.algorithm} (${vector.name})",
                )

                // Ensure digest() reset works by re-using the instance.
                digest.update(message)
                assertContentEquals(
                    expected = vector.digestBytes,
                    actual = digest.digest(),
                    message = "Digest reuse mismatch for ${manifest.algorithm} (${vector.name})",
                )

                // Exercise ByteString update path.
                val byteStringDigest = manifest.algorithm.createDigest()
                val messageByteString = vector.messageByteString
                byteStringDigest.update(messageByteString)
                assertEquals(
                    expected = vector.digestByteString,
                    actual = byteStringDigest.digestByteString(),
                    message = "Digest ByteString mismatch for ${manifest.algorithm} (${vector.name})",
                )

                // Verify reset() clears internal state even without calling digest().
                val resetDigest = manifest.algorithm.createDigest()
                resetDigest.update(message)
                resetDigest.reset()
                resetDigest.update(message)
                assertContentEquals(
                    expected = vector.digestBytes,
                    actual = resetDigest.digest(),
                    message = "Digest reset mismatch for ${manifest.algorithm} (${vector.name})",
                )
            }
        }
    }

    @Test
    fun `async helpers behave like synchronous variants`() = runTest {
        manifests.forEach { manifest ->
            val vector = manifest.vectors.first()

            val hasher = manifest.algorithm.createHasher()
            val digest = manifest.algorithm.createDigest()

            val asyncHash = hasher.hashAsync(vector.messageBytes)
            assertContentEquals(
                expected = vector.digestBytes,
                actual = asyncHash,
                message = "Async hasher mismatch for ${manifest.algorithm} (${vector.name})",
            )

            digest.update(vector.messageBytes)
            val asyncDigest = digest.digestAsync()
            assertContentEquals(
                expected = vector.digestBytes,
                actual = asyncDigest,
                message = "Async digest mismatch for ${manifest.algorithm} (${vector.name})",
            )
        }
    }


}
