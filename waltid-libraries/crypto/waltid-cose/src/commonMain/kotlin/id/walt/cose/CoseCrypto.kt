package id.walt.cose

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType


/** A suspendable interface for a COSE-compatible signer. */
fun interface CoseSigner {
    suspend fun sign(data: ByteArray): ByteArray
}

/** A suspendable interface for a COSE-compatible verifier. */
fun interface CoseVerifier {
    suspend fun verify(data: ByteArray, signature: ByteArray): Boolean
}

/** Adapts your [Key] to a [CoseSigner]. */
fun Key.toCoseSigner(): CoseSigner {
    require(this.hasPrivateKey) { "Key must have a private part to be used as a signer." }
    return CoseSigner { dataToSign ->
        // TODO: check if RAW or DER signature
        this.signRaw(dataToSign) as ByteArray
    }
}

/** Adapt [Key] to a [CoseVerifier]. */
fun Key.toCoseVerifier(): CoseVerifier =
    CoseVerifier { data, signature ->
        // TODO: check if RAW or DER signature
        this.verifyRaw(signed = signature, detachedPlaintext = data).isSuccess
    }

/** Map [KeyType] to a standard COSE Algorithm ID. */
fun KeyType.toCoseAlgorithm(): Int? = when (this) {
    KeyType.Ed25519 -> Cose.Algorithm.EdDSA
    KeyType.secp256r1 -> Cose.Algorithm.ES256
    KeyType.secp256k1 -> -47 // ES256K
    KeyType.RSA -> -257    // RS256
}


// --- Helper function to convert from DER to raw COSE format ---
// This is a simplified implementation.
private fun derToRaw(derSignature: ByteArray): ByteArray {
    if (derSignature[0] != 0x30.toByte() || derSignature.size < 8) {
        throw IllegalArgumentException("Invalid DER signature format")
    }
    // Simple parsing, assuming positive integers
    val rOffset = 4
    val rLength = derSignature[3].toInt()
    val r = derSignature.sliceArray(rOffset until rOffset + rLength).takeLast(32).toByteArray()

    val sOffset = rOffset + rLength + 2
    val sLength = derSignature[sOffset - 1].toInt()
    val s = derSignature.sliceArray(sOffset until sOffset + sLength).takeLast(32).toByteArray()

    val rPadded = ByteArray(32 - r.size) + r
    val sPadded = ByteArray(32 - s.size) + s

    return rPadded + sPadded
}

// --- Helper function to convert from raw COSE to DER format ---
private fun rawToDer(rawSignature: ByteArray): ByteArray {
    if (rawSignature.size != 64) throw IllegalArgumentException("Invalid raw signature length")
    val r = rawSignature.copyOfRange(0, 32)
    val s = rawSignature.copyOfRange(32, 64)

    // Ensure R and S are positive by prepending a zero byte if the high bit is set
    val rBytes = if (r[0] < 0) byteArrayOf(0) + r else r
    val sBytes = if (s[0] < 0) byteArrayOf(0) + s else s

    val rLength = rBytes.size
    val sLength = sBytes.size
    val totalLength = rLength + sLength + 4 // 2 tags, 2 lengths

    val der = ByteArray(totalLength + 2)
    var offset = 0

    // SEQUENCE tag and total length
    der[offset++] = 0x30.toByte()
    der[offset++] = totalLength.toByte()

    // INTEGER tag for R and its length
    der[offset++] = 0x02.toByte()
    der[offset++] = rLength.toByte()

    // Copy R bytes
    rBytes.copyInto(destination = der, destinationOffset = offset)
    offset += rLength

    // INTEGER tag for S and its length
    der[offset++] = 0x02.toByte()
    der[offset++] = sLength.toByte()

    // Copy S bytes
    sBytes.copyInto(destination = der, destinationOffset = offset)

    return der
}
