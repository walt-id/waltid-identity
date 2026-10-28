package id.walt.ades.tsp

import id.walt.ades.asn1.Der
import id.walt.ades.asn1.DerWriter
import id.walt.ades.crypto.AdesDigestAlgorithm
import id.walt.ades.transport.AdesTransport
import kotlin.random.Random

/** Source of RFC 3161 time-stamp tokens over a message imprint. */
fun interface TimestampSource {
    /** Returns the DER-encoded TimeStampToken (ContentInfo) for [digest] computed with [algorithm]. */
    suspend fun timestamp(algorithm: AdesDigestAlgorithm, digest: ByteArray): ByteArray
}

class TimestampException(message: String) : RuntimeException(message)

/** RFC 3161 HTTP client. */
class Rfc3161TimestampClient(
    private val url: String,
    private val transport: AdesTransport,
    private val policyOid: String? = null,
    private val headers: Map<String, String> = emptyMap(),
    private val useNonce: Boolean = true,
) : TimestampSource {

    override suspend fun timestamp(algorithm: AdesDigestAlgorithm, digest: ByteArray): ByteArray {
        val nonce = if (useNonce) Random.nextBytes(8).also { it[0] = (it[0].toInt() and 0x7f).toByte() } else null
        val request = buildRequest(algorithm, digest, nonce)
        val response = transport.post(url, "application/timestamp-query", request, headers)
        val token = parseResponse(response)
        val tst = TimeStampToken(token)
        if (!tst.tstInfo.messageImprint.contentEquals(digest)) throw TimestampException("TSA returned a token for a different imprint")
        if (nonce != null && tst.tstInfo.nonce != null && !tst.tstInfo.nonce.dropWhile { it == 0.toByte() }.toByteArray()
                .contentEquals(nonce.dropWhile { it == 0.toByte() }.toByteArray())
        ) throw TimestampException("TSA nonce mismatch")
        return token
    }

    fun buildRequest(algorithm: AdesDigestAlgorithm, digest: ByteArray, nonce: ByteArray?): ByteArray = DerWriter.sequence(
        listOfNotNull(
            DerWriter.integer(1),
            DerWriter.sequence(DerWriter.algorithmIdentifier(algorithm.oid, DerWriter.nullValue()), DerWriter.octetString(digest)),
            policyOid?.let(DerWriter::oid),
            nonce?.let(DerWriter::unsignedInteger),
            DerWriter.boolean(true),
        )
    )

    companion object {
        /** Extracts the TimeStampToken from a TimeStampResp, failing on rejection. */
        fun parseResponse(response: ByteArray): ByteArray {
            val resp = Der.parseFirst(response).expectSequence()
            val statusInfo = resp.child(0)
            val status = statusInfo.child(0).asInt()
            if (status != 0 && status != 1) {
                val text = statusInfo.children.getOrNull(1)?.takeIf { it.isUniversal(Der.SEQUENCE) }?.children?.joinToString { it.asString() }
                throw TimestampException("TSA rejected request: status=$status ${text ?: ""}".trim())
            }
            return resp.children.getOrNull(1)?.encoded ?: throw TimestampException("TSA response contains no token")
        }
    }
}
