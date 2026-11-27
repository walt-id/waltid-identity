package id.walt.wallet2.handlers

import id.walt.dcql.DcqlMatcher
import id.walt.dcql.models.DcqlQuery
import id.walt.verifier.openid.transactiondata.decodeList

internal fun DcqlQuery.requiredCredentialRequirements(): List<PresentationCredentialRequirement> =
    credentialSets
        ?.takeIf { it.isNotEmpty() }
        ?.let { sets ->
            sets.filter { it.required }
                .map { PresentationCredentialRequirement(options = it.options) }
        }
        ?: listOf(PresentationCredentialRequirement(options = listOf(credentials.map { it.id })))

internal fun List<PresentationCredentialRequirement>.satisfiedBy(selectedQueryIds: Set<String>): Boolean =
    all { requirement ->
        requirement.options.any { option ->
            option.isNotEmpty() && option.all { queryId -> queryId in selectedQueryIds }
        }
    }

internal fun List<PresentationCredentialSelection>.requireValidPresentationCredentialSelection() {
    require(isNotEmpty()) {
        "At least one credential option must be selected for presentation"
    }
    require(all { it.queryId.isNotBlank() && it.credentialId.isNotBlank() }) {
        "Selected presentation credential options must include non-blank query and credential IDs"
    }
    val duplicateSelection = groupingBy { it }
        .eachCount()
        .entries
        .firstOrNull { (_, count) -> count > 1 }
        ?.key
    require(duplicateSelection == null) {
        "Selected presentation credential options must not contain duplicate query and credential IDs"
    }
}

internal fun List<PresentationDisclosureSelection>.requireValidPresentationDisclosureSelection() {
    require(all { it.queryId.isNotBlank() && it.credentialId.isNotBlank() && it.path.isNotBlank() }) {
        "Selected presentation disclosure options must include non-blank query IDs, credential IDs, and paths"
    }
}

internal fun Map<String, List<DcqlMatcher.DcqlMatchResult>>.selectCredentialOptions(
    selectedCredentialOptions: List<PresentationCredentialSelection>,
    selectedDisclosureOptions: List<PresentationDisclosureSelection>? = null,
): Map<String, List<DcqlMatcher.DcqlMatchResult>> {
    selectedCredentialOptions.requireValidPresentationCredentialSelection()
    selectedDisclosureOptions?.requireValidPresentationDisclosureSelection()
    val selectedOptions = selectedCredentialOptions.toSet()
    val availableOptions = flatMap { (queryId, results) ->
        results.map { result -> result.toPresentationCredentialSelection(queryId) }
    }.toSet()

    val unknownSelection = selectedOptions.firstOrNull { selection -> selection !in availableOptions }
    require(unknownSelection == null) {
        "Selected credential option does not match the presentation preview"
    }

    val multipleAllowedByQueryId = mapValues { (_, results) ->
        results.firstOrNull()?.originalQuery?.multiple == true
    }
    val invalidMultipleSelection = selectedOptions
        .groupBy { selection -> selection.queryId }
        .entries
        .firstOrNull { (queryId, selections) ->
            selections.size > 1 && multipleAllowedByQueryId[queryId] != true
        }
    require(invalidMultipleSelection == null) {
        "Selected credential options must not contain multiple credentials for a non-multiple presentation query"
    }

    val selectedDisclosurePathsByOption = selectedDisclosureOptions
        ?.groupBy(
            keySelector = { selection ->
                PresentationCredentialSelection(
                    queryId = selection.queryId,
                    credentialId = selection.credentialId,
                )
            },
            valueTransform = { selection -> selection.path },
        )
        ?.mapValues { (_, paths) -> paths.toSet() }
    val unselectedDisclosureOption = selectedDisclosurePathsByOption
        ?.keys
        ?.firstOrNull { selection -> selection !in selectedOptions }
    require(unselectedDisclosureOption == null) {
        "Selected disclosure option does not match a selected credential option"
    }

    return mapValues { (queryId, results) ->
        results.filter { result ->
            result.toPresentationCredentialSelection(queryId) in selectedOptions
        }.map { result ->
            if (selectedDisclosurePathsByOption == null) {
                result
            } else {
                val option = result.toPresentationCredentialSelection(queryId)
                result.selectDisclosures(
                    selectedPaths = selectedDisclosurePathsByOption[option].orEmpty(),
                )
            }
        }
    }.filterValues { it.isNotEmpty() }
        .also { selected ->
            require(selected.isNotEmpty()) {
                "At least one selected credential option must match the presentation request"
            }
        }
}

internal fun validateSelectedTransactionDataCredentials(
    transactionData: List<String>,
    selectedQueryIds: Set<String>,
) {
    decodeList(transactionData).forEach { decoded ->
        val selectedTransactionCredentialIds = decoded.transactionData.credentialIds
            .filter { it in selectedQueryIds }
        require(selectedTransactionCredentialIds.size == 1) {
            "transaction_data credential_ids must reference exactly one selected credential for transaction authorization"
        }
    }
}

private fun DcqlMatcher.DcqlMatchResult.toPresentationCredentialSelection(queryId: String) =
    PresentationCredentialSelection(
        queryId = queryId,
        credentialId = credential.id,
    )
