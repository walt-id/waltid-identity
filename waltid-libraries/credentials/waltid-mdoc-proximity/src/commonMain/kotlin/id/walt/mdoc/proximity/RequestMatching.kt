package id.walt.mdoc.proximity

import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.ElementReference
import id.walt.mdoc.objects.deviceretrieval.ItemsRequest
import id.walt.mdoc.objects.deviceretrieval.UseCase

/** Inventory projection needed by the protocol matcher; storage and key handles remain outside this module. */
class MdocCredentialCandidate(
    val id: String,
    val docType: String,
    issuerAuthorityKeyIdentifiers: Collection<ImmutableBytes>,
    availableElements: Collection<ElementReference>,
) {
    val issuerAuthorityKeyIdentifiers: Set<ImmutableBytes> = issuerAuthorityKeyIdentifiers.toSet()
    val availableElements: Set<ElementReference> = availableElements.toSet()

    init {
        require(id.isNotBlank() && docType.isNotBlank())
        require(availableElements.isNotEmpty()) { "An mdoc candidate must expose at least one data element" }
    }
}

data class SelectedElement(
    val reference: ElementReference,
    val intentToRetain: Boolean,
    /** Requested elements satisfied by this returned element; empty means the element was requested directly. */
    val satisfiesAlternativesFor: Set<ElementReference> = emptySet(),
)

data class SelectedDocument(
    val requestIndex: Int,
    val credentialId: String,
    val elements: Set<SelectedElement>,
    val zkp: SelectedZkpSystem? = null,
)

data class SelectedUseCase(
    val index: Int,
    val mandatory: Boolean,
    val documentSet: List<Int>,
    val purposeHints: Map<String, Int>,
)

data class MdocRequestSelection(
    val documents: List<SelectedDocument>,
    val useCases: List<SelectedUseCase>,
    /** Every credential choice that can satisfy one of the selected document requests. */
    val eligibleDocuments: List<SelectedDocument> = documents,
) {
    init {
        require(documents.isNotEmpty()) { "A successful selection must contain at least one document" }
        require(documents.map { it.credentialId to it.requestIndex }.distinct().size == documents.size)
        require(eligibleDocuments.isNotEmpty()) { "A successful selection must retain eligible document choices" }
        require(eligibleDocuments.map { it.credentialId to it.requestIndex }.distinct().size == eligibleDocuments.size)
        require(documents.all { selected ->
            eligibleDocuments.any {
                it.requestIndex == selected.requestIndex && it.credentialId == selected.credentialId
            }
        }) { "Every selected document must be one of the eligible choices" }
    }
}

sealed interface MdocRequestMatchResult {
    data class Matched(val selection: MdocRequestSelection) : MdocRequestMatchResult
    data class Unsatisfied(val reason: String, val unsatisfiedRequestIndices: Set<Int>) : MdocRequestMatchResult
}

/** Deterministic edition-2 matcher. Consent and reader-authorization policy are intentionally separate. */
class MdocRequestMatcher(
    private val zkpRegistry: ZkpRegistry = ZkpRegistry.EMPTY,
) {
    suspend fun match(
        request: DeviceRequest,
        candidates: List<MdocCredentialCandidate>,
    ): MdocRequestMatchResult {
        require(candidates.map { it.id }.distinct().size == candidates.size) { "Credential candidate IDs must be unique" }
        validateRequestReferences(request)

        val eligible = request.docRequests.mapIndexed { index, docRequest ->
            val items = docRequest.itemsRequest.value
            candidates.filter { candidate ->
                candidate.docType == items.docType && issuerAccepted(items, candidate)
            }.mapNotNull { candidate ->
                selectElements(items, candidate)?.let { elements ->
                    val zkp = zkpRegistry.select(items.requestInfo?.zkRequest, candidate)
                    if (items.requestInfo?.zkRequest?.zkRequired == true && zkp == null) null
                    else SelectedDocument(index, candidate.id, elements, zkp)
                }
            }
        }
        val matches = request.docRequests.mapIndexed { index, docRequest ->
            val suitable = eligible[index]
            val items = docRequest.itemsRequest.value
            when (items.requestInfo?.uniqueDocSetRequired) {
                false -> suitable
                true -> suitable.take(1)
                // Reader support for multiple responses is undefined when the field is absent.
                null -> suitable.take(1)
            }
        }

        val useCases = request.deviceRequestInfo?.value?.useCases
        if (useCases == null) {
            val selected = matches.flatten()
            return if (selected.isEmpty()) MdocRequestMatchResult.Unsatisfied(
                "None of the requested documents can be satisfied",
                matches.indices.toSet(),
            ) else MdocRequestMatchResult.Matched(
                MdocRequestSelection(
                    documents = selected,
                    useCases = emptyList(),
                    eligibleDocuments = eligible.flatten(),
                )
            )
        }

        val satisfiable = useCases.mapIndexedNotNull { index, useCase ->
            firstSatisfiableSet(useCase, matches)?.let { set ->
                SelectedUseCase(index, useCase.mandatory, set, useCase.purposeHints.orEmpty().toMap())
            }
        }
        val unsatisfiedMandatory = useCases.indices.filter { useCases[it].mandatory && satisfiable.none { selected -> selected.index == it } }
        if (unsatisfiedMandatory.isNotEmpty()) {
            return MdocRequestMatchResult.Unsatisfied(
                "A mandatory use case cannot be satisfied",
                unsatisfiedMandatory.flatMap { useCases[it].documentSets.flatten() }.map(UInt::toInt).toSet(),
            )
        }
        val selectedUseCases = satisfiable.filter { it.mandatory }.ifEmpty { satisfiable.take(1) }
        if (selectedUseCases.isEmpty()) {
            return MdocRequestMatchResult.Unsatisfied("No use case can be satisfied", matches.indices.toSet())
        }
        val selectedDocuments = selectedUseCases
            .flatMap { it.documentSet }
            .distinct()
            .flatMap { matches[it] }
            .distinctBy { it.requestIndex to it.credentialId }
        val selectedRequestIndices = selectedUseCases.flatMap(SelectedUseCase::documentSet).toSet()
        return MdocRequestMatchResult.Matched(
            MdocRequestSelection(
                documents = selectedDocuments,
                useCases = selectedUseCases,
                eligibleDocuments = selectedRequestIndices.flatMap(eligible::get),
            )
        )
    }

    private fun firstSatisfiableSet(useCase: UseCase, matches: List<List<SelectedDocument>>): List<Int>? =
        useCase.documentSets.firstNotNullOfOrNull { documentSet ->
            documentSet.map(UInt::toInt).takeIf { indices -> indices.all { matches[it].isNotEmpty() } }
        }

    private fun issuerAccepted(items: ItemsRequest, candidate: MdocCredentialCandidate): Boolean {
        val accepted = items.requestInfo?.issuerIdentifiers ?: return true
        return accepted.any { requested ->
            candidate.issuerAuthorityKeyIdentifiers.any { candidateIdentifier -> candidateIdentifier.contentEquals(requested) }
        }
    }

    private fun selectElements(
        items: ItemsRequest,
        candidate: MdocCredentialCandidate,
    ): Set<SelectedElement>? {
        val alternatives = items.requestInfo?.alternativeDataElements.orEmpty().associateBy { it.requestedElement }
        val selected = linkedMapOf<ElementReference, SelectedElement>()
        fun include(reference: ElementReference, retain: Boolean, alternativeFor: ElementReference? = null) {
            val previous = selected[reference]
            selected[reference] = SelectedElement(
                reference = reference,
                intentToRetain = retain || previous?.intentToRetain == true,
                satisfiesAlternativesFor = previous?.satisfiesAlternativesFor.orEmpty() + listOfNotNull(alternativeFor),
            )
        }
        items.namespaces.forEach { (namespace, requested) ->
            requested.entries.forEach { item ->
                val reference = ElementReference(namespace, item.key)
                if (reference in candidate.availableElements) {
                    include(reference, item.value)
                } else {
                    val replacement = alternatives[reference]?.alternativeElementSets
                        ?.firstOrNull { set -> set.all { it in candidate.availableElements } }
                        ?: return null
                    replacement.forEach { include(it, item.value, reference) }
                }
            }
        }
        return selected.values.toSet()
    }

    private fun validateRequestReferences(request: DeviceRequest) {
        request.docRequests.forEach { docRequest ->
            val items = docRequest.itemsRequest.value
            val requested = items.namespaces.flatMap { (namespace, values) ->
                values.entries.map { ElementReference(namespace, it.key) }
            }.toSet()
            val alternatives = items.requestInfo?.alternativeDataElements.orEmpty()
            require(alternatives.map { it.requestedElement }.distinct().size == alternatives.size) {
                "AlternativeDataElements cannot repeat a requested element"
            }
            require(alternatives.all { it.requestedElement in requested }) {
                "AlternativeDataElements must reference an element in nameSpaces"
            }
            require(alternatives.all { alternative ->
                alternative.alternativeElementSets.distinct().size == alternative.alternativeElementSets.size &&
                    alternative.alternativeElementSets.all { it.distinct().size == it.size }
            }) { "AlternativeDataElements cannot repeat an alternative set or element" }
        }
        request.deviceRequestInfo?.value?.useCases.orEmpty().forEach { useCase ->
            require(useCase.documentSets.flatten().all { it < request.docRequests.size.toUInt() }) {
                "UseCase references a DocRequest outside the request"
            }
        }
    }
}
