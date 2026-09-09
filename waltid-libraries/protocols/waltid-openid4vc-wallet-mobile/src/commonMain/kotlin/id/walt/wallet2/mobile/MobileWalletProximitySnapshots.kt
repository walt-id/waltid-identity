package id.walt.wallet2.mobile

/** Private proximity ownership boundaries; host DTOs never become retained policy by alias. */
internal fun ProximityConfiguration.snapshot(): ProximityConfiguration = copy(
    engagementMethods = engagementMethods.toSet(),
    retrievalMethods = retrievalMethods.toSet(),
)

internal fun ProximitySubmission.snapshot(): ProximitySubmission = copy(
    documents = documents.map { it.copy(disclosedElements = it.disclosedElements.toSet()) },
)

internal fun ProximityApplicationAuthorization.snapshot(): ProximityApplicationAuthorization = copy(
    details = details.toList(),
    compatibleCredentialIds = compatibleCredentialIds.toSet(),
    deviceSignedElements = deviceSignedElements.toList(),
)

private fun ProximityRequestedElement.snapshot(): ProximityRequestedElement = copy(
    satisfiesRequestedElements = satisfiesRequestedElements.toList(),
)

internal fun ProximityReview.snapshot(): ProximityReview = copy(
    documents = documents.map { document ->
        document.copy(credentialOptions = document.credentialOptions.map { option ->
            option.copy(requestedElements = option.requestedElements.map { it.snapshot() })
        })
    },
    readerAuthentication = readerAuthentication.toList(),
    useCases = useCases.map { it.copy(documentRequestIndices = it.documentRequestIndices.toList(), purposeHints = it.purposeHints.toList()) },
    applicationAuthorizations = applicationAuthorizations.map { it.snapshot() },
)

internal fun ProximityApplicationProfileInput.snapshot(): ProximityApplicationProfileInput = copy(
    credentials = credentials.toList(),
    requestedDocuments = requestedDocuments.map { document ->
        document.copy(requestedElements = document.requestedElements.map { it.snapshot() })
    },
    readerAuthentication = readerAuthentication.toList(),
)
