@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.verifier2.handlers.sessioncreation.annexc

import id.walt.mdoc.objects.SessionTranscript
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborObjectAsArray

@Serializable
@CborObjectAsArray
data class ReaderAuthenticationAll(
    val context: String,
    val sessionTranscript: SessionTranscript,
    val itemsRequestBytesAll: List<ByteArray>,
    val docRequestsInfoBytes: ByteArray? = null
) {
    companion object {
        val CONTEXT = "ReaderAuthenticationAll"
    }
}

