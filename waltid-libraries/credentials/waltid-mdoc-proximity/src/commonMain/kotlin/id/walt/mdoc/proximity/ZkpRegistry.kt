package id.walt.mdoc.proximity

import id.walt.mdoc.objects.deviceretrieval.ZkRequest
import id.walt.mdoc.objects.deviceretrieval.ZkSystemSpec
import id.walt.mdoc.objects.deviceretrieval.ZkDocument
import id.walt.mdoc.objects.SessionTranscript

data class SelectedZkpSystem(
    val specification: ZkSystemSpec,
    val providerId: String,
) {
    val systemId: String get() = specification.zkSystemId
    val system: String get() = specification.system
}

/** A proof implementation is optional and isolated from the core mdoc/session machinery. */
interface ZkpProvider {
    val id: String
    val system: String
    suspend fun supports(candidate: MdocCredentialCandidate, specification: ZkSystemSpec): Boolean
    suspend fun createProof(input: ZkpProofInput): ZkDocument
}

data class ZkpProofInput(
    val credentialId: String,
    val specification: ZkSystemSpec,
    val selectedElements: Set<SelectedElement>,
    val transcript: SessionTranscript,
)

class ZkpRegistry(providers: List<ZkpProvider>) {
    private val providers = providers.toList()

    init {
        require(this.providers.map { it.id }.distinct().size == this.providers.size) { "ZKP provider IDs must be unique" }
        require(this.providers.all { it.id.isNotBlank() && it.system.isNotBlank() })
    }

    suspend fun select(request: ZkRequest?, candidate: MdocCredentialCandidate): SelectedZkpSystem? {
        if (request == null) return null
        request.systemSpecs.forEach { specification ->
            providers.firstOrNull { provider ->
                provider.system == specification.system && provider.supports(candidate, specification)
            }?.let { provider ->
                return SelectedZkpSystem(specification, provider.id)
            }
        }
        return null
    }

    suspend fun createProof(selection: SelectedZkpSystem, input: ZkpProofInput): ZkDocument {
        val provider = providers.singleOrNull { it.id == selection.providerId }
            ?: throw IllegalArgumentException("Selected ZKP provider is no longer registered")
        require(provider.system == selection.system && input.specification == selection.specification) {
            "ZKP selection does not match the active request"
        }
        return provider.createProof(input)
    }

    companion object {
        val EMPTY = ZkpRegistry(emptyList())
    }
}
