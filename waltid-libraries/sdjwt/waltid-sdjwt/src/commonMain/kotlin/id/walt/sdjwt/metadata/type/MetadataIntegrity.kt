package id.walt.sdjwt.metadata.type

import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.hash.sha2.SHA384
import org.kotlincrypto.hash.sha2.SHA512
import kotlin.io.encoding.Base64

/** W3C SRI 2016 processing, shared by metadata consumers and publishers. */
object MetadataIntegrity {
    private val syntax = Regex("(sha256|sha384|sha512)-([A-Za-z0-9+/_-]+={0,2})(?:\\?[\\x21-\\x7E]*)?")

    private fun digests(reference: String): List<Pair<Int, ByteArray?>> {
        val tokens = reference.trim().split(Regex("\\s+"))
        require(tokens.size <= 8) { "Too many integrity expressions" }
        return tokens.mapNotNull { token ->
            val match = syntax.matchEntire(token) ?: return@mapNotNull null
            val size = when (match.groupValues[1]) { "sha256" -> 32; "sha384" -> 48; else -> 64 }
            val encoded = match.groupValues[2].replace('-', '+').replace('_', '/')
            val digest = try {
                Base64.Default.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(encoded)
            } catch (_: IllegalArgumentException) { null }
            size to digest?.takeIf { it.size == size }
        }.also { require(it.isNotEmpty()) { "No supported integrity expression" } }
    }

    fun requireValid(reference: String) {
        val digests = digests(reference)
        val strongest = digests.maxOf { it.first }
        require(digests.any { it.first == strongest && it.second != null }) { "Malformed integrity digest" }
    }

    fun verify(bytes: ByteArray, reference: String) {
        val digests = try { digests(reference) } catch (cause: IllegalArgumentException) {
            throw TypeMetadataException(TypeMetadataFailure.INVALID_METADATA, cause.message.orEmpty(), cause)
        }
        val strongest = digests.maxOf { it.first }
        val actual = when (strongest) { 32 -> SHA256().digest(bytes); 48 -> SHA384().digest(bytes); else -> SHA512().digest(bytes) }
        if (digests.none { it.first == strongest && it.second?.contentEquals(actual) == true }) {
            throw TypeMetadataException(TypeMetadataFailure.INTEGRITY_MISMATCH, "Type metadata integrity mismatch")
        }
    }

    fun sha256(bytes: ByteArray): String = "sha256-" + Base64.Default.encode(SHA256().digest(bytes))
}
