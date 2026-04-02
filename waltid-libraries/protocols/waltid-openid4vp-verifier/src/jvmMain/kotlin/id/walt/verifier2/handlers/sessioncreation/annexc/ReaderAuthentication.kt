package id.walt.verifier2.handlers.sessioncreation.annexc

import id.walt.mdoc.objects.SessionTranscript
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborObjectAsArray

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@CborObjectAsArray
data class ReaderAuthentication(
    /** must be "ReaderAuthentication" */
    val context: String,

    /** The shared Session Transcript */
    val sessionTranscript: SessionTranscript,

    /** The serialized ItemsRequest for this specific document only */
    val itemsRequestBytes: ByteArray
) {
    companion object {
        const val CONTEXT = "ReaderAuthentication"
    }
}
