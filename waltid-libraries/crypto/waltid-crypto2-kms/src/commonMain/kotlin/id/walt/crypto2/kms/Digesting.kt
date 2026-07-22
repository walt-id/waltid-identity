package id.walt.crypto2.kms

import dev.whyoleg.cryptography.CryptographyAlgorithmId
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.Digest
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.SHA384
import dev.whyoleg.cryptography.algorithms.SHA512
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.DigestValue

suspend fun digest(data: ByteArray, algorithm: DigestAlgorithm): DigestValue = DigestValue(
    algorithm = algorithm,
    value = CryptographyProvider.Default.get(algorithm.toCryptographyDigest()).hasher().hash(data),
)

private fun DigestAlgorithm.toCryptographyDigest(): CryptographyAlgorithmId<Digest> = when (this) {
    DigestAlgorithm.SHA_256 -> SHA256
    DigestAlgorithm.SHA_384 -> SHA384
    DigestAlgorithm.SHA_512 -> SHA512
    else -> throw IllegalArgumentException("Unsupported KMS digest: $name")
}
