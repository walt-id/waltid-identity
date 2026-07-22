package id.walt.crypto2.algorithms

import id.walt.crypto2.serialization.BinaryData

data class DigestValue(
    val algorithm: DigestAlgorithm,
    val value: BinaryData,
) {
    constructor(algorithm: DigestAlgorithm, value: ByteArray) : this(algorithm, BinaryData(value))

    init {
        require(value.size > 0) { "Digest value cannot be empty" }
        algorithm.outputSizeBytes?.let { expectedSize ->
            require(value.size == expectedSize) {
                "${algorithm.name} digest must be $expectedSize bytes"
            }
        }
    }
}
