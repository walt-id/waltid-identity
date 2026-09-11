package id.walt.certificate.x509.revocation

import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import kotlinx.io.bytestring.ByteString
import kotlin.time.Instant

internal data class ParsedCrl(
    val tbs: ByteString,
    val signature: ByteString,
    val algorithm: SignatureAlgorithm,
    val issuer: ByteString,
    val thisUpdate: Instant,
    val nextUpdate: Instant,
    val authorityKeyIdentifier: ByteString,
    val revoked: Map<ByteString, RevokedSerial>,
)

internal data class RevokedSerial(val time: Instant, val reason: Int?)

internal fun parseCompleteCrl(bytes: ByteString): ParsedCrl {
    requireCrl(bytes.size in 1..2_097_152)
    val outer = DerReader(bytes.toByteArray()).single(0x30).reader()
    val tbs = outer.read(0x30)
    val algorithm = outer.read(0x30)
    val signature = outer.read(0x03).content
    requireCrl(outer.done && signature.size > 1 && signature[0] == 0.toByte())
    val fields = tbs.reader()
    // The supported RFC 5280 profile is v2 (INTEGER 1), including AKI and CRL number.
    requireCrl(fields.read(0x02).content.contentEquals(byteArrayOf(1)), CrlFailure.UNSUPPORTED_PROFILE)
    requireCrl(fields.read(0x30).encoded.contentEquals(algorithm.encoded))
    val issuer = fields.read(0x30)
    requireCrl(issuer.content.isNotEmpty())
    val thisUpdate = fields.readTime()
    requireCrl(fields.peek() == 0x17 || fields.peek() == 0x18, CrlFailure.UNSUPPORTED_PROFILE)
    val nextUpdate = fields.readTime()
    requireCrl(thisUpdate < nextUpdate)
    val revoked = linkedMapOf<ByteString, RevokedSerial>()
    if (fields.peek() == 0x30) {
        val entries = fields.read(0x30).reader()
        requireCrl(!entries.done)
        while (!entries.done) {
            requireCrl(revoked.size < 10_000, CrlFailure.UNSUPPORTED_PROFILE)
            val entry = entries.read(0x30).reader()
            val serial = normaliseSerial(entry.read(0x02).content)
            val revokedAt = entry.readTime()
            requireCrl(revokedAt <= thisUpdate)
            val extensions = if (entry.done) emptyMap() else entry.read(0x30).readExtensions()
            requireCrl(entry.done)
            requireCrl("551d1d" !in extensions, CrlFailure.UNSUPPORTED_PROFILE) // certificateIssuer
            extensions.values.forEach { extension ->
                requireCrl(!extension.critical, CrlFailure.UNSUPPORTED_PROFILE)
            }
            val reason = extensions["551d15"]?.let {
                val value = DerReader(it.value).single(0x0a).content
                requireCrl(value.size == 1)
                value[0].toInt().also { reason ->
                    // removeFromCRL (8) belongs to delta CRLs, which this verifier does not process.
                    requireCrl(reason in setOf(0, 1, 2, 3, 4, 5, 6, 9, 10), CrlFailure.UNSUPPORTED_PROFILE)
                }
            }
            extensions["551d18"]?.let {
                DerReader(it.value).apply { requireCrl(peek() == 0x18); readTime(); requireCrl(done) }
            }
            requireCrl(revoked.put(serial, RevokedSerial(revokedAt, reason)) == null)
        }
    }
    requireCrl(fields.peek() == 0xa0, CrlFailure.UNSUPPORTED_PROFILE)
    val extensions = fields.read(0xa0).reader().single(0x30).readExtensions()
    requireCrl(fields.done)
    requireCrl("551d1b" !in extensions && "551d1c" !in extensions, CrlFailure.UNSUPPORTED_PROFILE) // delta / IDP
    extensions.values.forEach { extension ->
        requireCrl(!extension.critical, CrlFailure.UNSUPPORTED_PROFILE)
    }
    val aki = extensions["551d23"] ?: throw CrlValidationException(CrlFailure.UNSUPPORTED_PROFILE)
    val akiFields = DerReader(aki.value).single(0x30).reader()
    val keyId = akiFields.read(0x80).content
    requireCrl(keyId.isNotEmpty())
    requireCrl(akiFields.done, CrlFailure.UNSUPPORTED_PROFILE)
    val number = extensions["551d14"] ?: throw CrlValidationException(CrlFailure.UNSUPPORTED_PROFILE)
    val crlNumber = DerReader(number.value).single(0x02).content
    validateUnsignedInteger(crlNumber, allowZero = true)
    return ParsedCrl(ByteString(tbs.encoded), ByteString(signature.copyOfRange(1, signature.size)),
        algorithm.signatureAlgorithm(), ByteString(issuer.encoded), thisUpdate, nextUpdate, ByteString(keyId), revoked)
}

internal fun normaliseSerial(bytes: ByteArray): ByteString {
    validateUnsignedInteger(bytes, allowZero = false)
    return ByteString(if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes)
}

private fun validateUnsignedInteger(bytes: ByteArray, allowZero: Boolean) {
    requireCrl(bytes.isNotEmpty() && bytes.size <= 20 && bytes[0].toInt() >= 0)
    requireCrl(bytes.size == 1 || bytes[0] != 0.toByte() || bytes[1].toInt() < 0)
    requireCrl(allowZero || bytes.any { it != 0.toByte() })
}

private fun DerValue.signatureAlgorithm(): SignatureAlgorithm {
    val values = reader()
    val oid = values.readOid().toHexString()
    val rsa = oid.startsWith("2a864886f70d0101")
    if (!values.done) {
        requireCrl(rsa && values.read(0x05).content.isEmpty(), CrlFailure.UNSUPPORTED_PROFILE)
    }
    requireCrl(values.done)
    val digest = when (oid) {
        "2a8648ce3d040302", "2a864886f70d01010b" -> DigestAlgorithm.SHA_256
        "2a8648ce3d040303", "2a864886f70d01010c" -> DigestAlgorithm.SHA_384
        "2a8648ce3d040304", "2a864886f70d01010d" -> DigestAlgorithm.SHA_512
        else -> throw CrlValidationException(CrlFailure.UNSUPPORTED_PROFILE)
    }
    return if (rsa) SignatureAlgorithm.RsaPkcs1(digest)
    else SignatureAlgorithm.Ecdsa(digest, EcdsaSignatureEncoding.DER)
}

private data class CrlExtension(val critical: Boolean, val value: ByteArray)

private fun DerValue.readExtensions(): Map<String, CrlExtension> {
    val fields = reader()
    val result = linkedMapOf<String, CrlExtension>()
    requireCrl(!fields.done)
    while (!fields.done) {
        requireCrl(result.size < 64, CrlFailure.UNSUPPORTED_PROFILE)
        val extension = fields.read(0x30).reader()
        val oid = extension.readOid()
        var critical = false
        if (extension.peek() == 0x01) {
            // DER omits a DEFAULT FALSE; TRUE has the sole canonical value FF.
            requireCrl(extension.read(0x01).content.contentEquals(byteArrayOf(0xff.toByte())))
            critical = true
        }
        val value = extension.read(0x04).content
        requireCrl(extension.done)
        requireCrl(result.put(oid.toHexString(), CrlExtension(critical, value)) == null)
    }
    return result
}

/** A bounded, nonrecursive DER cursor for the fixed CertificateList grammar. */
private class DerReader(private val bytes: ByteArray) {
    private var position = 0
    val done: Boolean get() = position == bytes.size
    fun peek(): Int? = if (done) null else bytes[position].toInt() and 0xff

    fun single(tag: Int): DerValue = read(tag).also { requireCrl(done) }

    fun read(tag: Int): DerValue {
        requireCrl(position < bytes.size && peek() == tag)
        val start = position++
        requireCrl(position < bytes.size)
        val first = bytes[position++].toInt() and 0xff
        val length = if (first < 128) first else {
            val count = first and 0x7f
            requireCrl(count in 1..3 && count <= bytes.size - position)
            requireCrl(bytes[position] != 0.toByte())
            var value = 0
            repeat(count) { value = (value shl 8) or (bytes[position++].toInt() and 0xff) }
            requireCrl(value >= 128)
            value
        }
        requireCrl(length <= bytes.size - position)
        val contentStart = position
        position += length
        return DerValue(bytes.copyOfRange(start, position), bytes.copyOfRange(contentStart, position))
    }

    fun readOid(): ByteArray = read(0x06).content.also { value ->
        requireCrl(value.isNotEmpty() && value.size <= 64)
        var firstOctet = true
        for (octet in value) {
            if (firstOctet) requireCrl(octet != 0x80.toByte())
            firstOctet = octet.toInt() and 0x80 == 0
        }
        requireCrl(firstOctet)
    }

    fun readTime(): Instant {
        val tag = peek()
        requireCrl(tag == 0x17 || tag == 0x18)
        val bytes = read(tag!!).content
        requireCrl(bytes.all { it.toInt() in 0..127 })
        val value = bytes.decodeToString()
        val yearDigits = if (tag == 0x17) 2 else 4
        requireCrl(value.length == yearDigits + 11 && value.last() == 'Z' && value.dropLast(1).all { it in '0'..'9' })
        val year = value.take(yearDigits).let {
            if (yearDigits == 4) it else (if (it.toInt() >= 50) "19" else "20") + it
        }
        val date = value.drop(yearDigits)
        return try {
            Instant.parse("$year-${date.take(2)}-${date.substring(2, 4)}T${date.substring(4, 6)}:${date.substring(6, 8)}:${date.substring(8, 10)}Z")
        } catch (_: IllegalArgumentException) {
            throw CrlValidationException(CrlFailure.INVALID_DER)
        }
    }
}

private data class DerValue(val encoded: ByteArray, val content: ByteArray) {
    fun reader(): DerReader = DerReader(content)
}
