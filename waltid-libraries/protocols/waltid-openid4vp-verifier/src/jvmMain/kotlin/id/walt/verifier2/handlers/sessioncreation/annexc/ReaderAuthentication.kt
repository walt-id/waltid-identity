@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.verifier2.handlers.sessioncreation.annexc

import id.walt.mdoc.objects.SessionTranscript
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborObjectAsArray

/**
 * Per-document reader authentication structure for ISO 18013-7.
 *
 * This is used when authenticating each DocRequest individually (readerAuth),
 * as opposed to ReaderAuthenticationAll which authenticates all requests together.
 *
 * Structure: ["ReaderAuthentication", SessionTranscript, ItemsRequestBytes]
 *
 * @see ISO/IEC 18013-5:2021, 9.1.4
 */
@Serializable
@CborObjectAsArray
data class ReaderAuthentication(
    val context: String = "ReaderAuthentication",
    val sessionTranscript: SessionTranscript,
    val itemsRequestBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ReaderAuthentication

        if (context != other.context) return false
        if (sessionTranscript != other.sessionTranscript) return false
        if (!itemsRequestBytes.contentEquals(other.itemsRequestBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = context.hashCode()
        result = 31 * result + sessionTranscript.hashCode()
        result = 31 * result + itemsRequestBytes.contentHashCode()
        return result
    }
}
