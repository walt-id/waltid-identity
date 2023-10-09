package id.walt.didlib.did.registrar

import id.walt.didlib.did.document.DidDocument
import kotlinx.serialization.Serializable

@Serializable
data class DidResult(
    val did: String,
    val didDocument: DidDocument
)
