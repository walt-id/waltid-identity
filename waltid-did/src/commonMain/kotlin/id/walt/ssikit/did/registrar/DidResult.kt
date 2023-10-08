package id.walt.ssikit.did.registrar

import id.walt.ssikit.did.document.DidDocument
import kotlinx.serialization.Serializable

@Serializable
data class DidResult(
    val did: String,
    val didDocument: DidDocument
)
