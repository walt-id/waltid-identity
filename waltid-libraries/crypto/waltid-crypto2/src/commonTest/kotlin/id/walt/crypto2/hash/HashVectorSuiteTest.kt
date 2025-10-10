package id.walt.crypto2.hash

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals

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

                // Verify ByteString overload mirrors byte[] behaviour
                val byteStringResult = hasher.hash(vector.messageByteString)
                assertContentEquals(
                    vector.digestByteString.toByteArray(),
                    byteStringResult.toByteArray(),
                    "Hasher ByteString mismatch for ${manifest.algorithm} (${vector.name})",
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
                    vector.digestBytes,
                    digest.digest(),
                    "Digest reuse mismatch for ${manifest.algorithm} (${vector.name})",
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
                vector.digestBytes,
                asyncHash,
                "Async hasher mismatch for ${manifest.algorithm} (${vector.name})",
            )

            digest.update(vector.messageBytes)
            val asyncDigest = digest.digestAsync()
            assertContentEquals(
                vector.digestBytes,
                asyncDigest,
                "Async digest mismatch for ${manifest.algorithm} (${vector.name})",
            )
        }
    }


}