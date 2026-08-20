package id.walt.walletdemo.compose.logic

/** Stable identity for one review island and its technical destination. */
data class WalletDemoReviewIslandId(val value: String)

/** The semantic role an island has across issuance, presentation, and credential details. */
enum class WalletDemoReviewIslandKind {
    Issuer,
    Verifier,
    Credential,
    Information,
    ValidityAndStatus,
    PurposeAndTransaction,
    RequiredAction,
}

/** Builds stored-credential details with the same information hierarchy as review surfaces. */
fun CredentialDetails.toStoredReviewIslands(): List<WalletDemoReviewIsland> {
    val summary = toCardDisplayData()
    val issuerName = issuerDisplay?.name.presentableOrNull()
        ?: this.summary.issuer.presentableOrNull()
        ?: "Issuer unavailable"

    return buildList {
        add(
            WalletDemoReviewIsland(
                id = WalletDemoReviewIslandId("credential"),
                kind = WalletDemoReviewIslandKind.Credential,
                context = WalletDemoReviewSurfaceContext.Stored,
                title = summary.title,
                subtitle = summary.credentialType ?: "Stored credential",
                visual = WalletDemoReviewVisual(
                    imageUri = summary.portrait?.encoded,
                    contentDescription = summary.portrait?.let { "Credential portrait" },
                    fallbackText = summary.title.firstOrNull()?.uppercase() ?: "C",
                ),
                expandedValues = listOf(
                    WalletDemoReviewValue("Holder", summary.holderName),
                ),
                technicalSections = listOf(
                    WalletDemoReviewTechnicalSection(
                        id = "credential-identity",
                        title = "Credential identity",
                        values = listOf(
                            WalletDemoReviewValue("Credential identifier", this@toStoredReviewIslands.summary.id),
                            WalletDemoReviewValue("Format", this@toStoredReviewIslands.summary.format),
                            WalletDemoReviewValue("Subject", this@toStoredReviewIslands.summary.subject),
                        ),
                    )
                ),
                initiallyExpanded = true,
            )
        )
        add(
            WalletDemoReviewIsland(
                id = WalletDemoReviewIslandId("issuer"),
                kind = WalletDemoReviewIslandKind.Issuer,
                context = WalletDemoReviewSurfaceContext.Stored,
                title = issuerName,
                subtitle = "Credential Issuer",
                visual = issuerDisplay.toReviewVisual(issuerName),
                expandedValues = listOf(
                    WalletDemoReviewValue("About", issuerDisplay?.description),
                ),
                technicalSections = listOf(
                    WalletDemoReviewTechnicalSection(
                        id = "issuer-identity",
                        title = "Issuer identity",
                        values = listOf(
                            WalletDemoReviewValue(
                                "Issuer identifier",
                                this@toStoredReviewIslands.summary.issuer,
                                linkUri = this@toStoredReviewIslands.summary.issuer,
                            ),
                            WalletDemoReviewValue("Selected display name", issuerDisplay?.name),
                            WalletDemoReviewValue("Logo source", issuerDisplay?.logoUri, linkUri = issuerDisplay?.logoUri),
                        ),
                    )
                ),
            )
        )
        if (groups.isNotEmpty()) {
            add(
                WalletDemoReviewIsland(
                    id = WalletDemoReviewIslandId("information"),
                    kind = WalletDemoReviewIslandKind.Information,
                    context = WalletDemoReviewSurfaceContext.Stored,
                    title = "Information",
                    subtitle = groups.flatMap(ClaimGroup::items).size.let { count ->
                        "$count ${if (count == 1) "field" else "fields"}"
                    },
                    visual = WalletDemoReviewVisual(fallbackText = "i"),
                    expandedValues = groups.flatMap { group ->
                        group.items.map { item ->
                            WalletDemoReviewValue(item.label, item.value.reviewText(), supportingText = group.title)
                        }
                    },
                    technicalSections = groups.mapIndexed { index, group ->
                        WalletDemoReviewTechnicalSection(
                            id = "stored-information-$index",
                            title = group.title,
                            values = group.items.map { item ->
                                WalletDemoReviewValue(item.path.id, item.rawValue ?: item.value.reviewText())
                            },
                        )
                    },
                    initiallyExpanded = true,
                )
            )
        }
        summary.validity?.let { validity ->
            add(
                WalletDemoReviewIsland(
                    id = WalletDemoReviewIslandId("validity-and-status"),
                    kind = WalletDemoReviewIslandKind.ValidityAndStatus,
                    context = WalletDemoReviewSurfaceContext.Stored,
                    title = "Dates and status",
                    subtitle = validity,
                    visual = WalletDemoReviewVisual(fallbackText = "✓"),
                    expandedValues = listOf(WalletDemoReviewValue("Available information", validity)),
                    technicalSections = listOf(
                        WalletDemoReviewTechnicalSection(
                            id = "stored-dates",
                            title = "Stored dates",
                            values = listOf(WalletDemoReviewValue("Added to wallet", this@toStoredReviewIslands.summary.addedAt)),
                        )
                    ),
                )
            )
        }
    }
}

/** The context that decides which details and controls an island may expose. */
enum class WalletDemoReviewSurfaceContext {
    Offered,
    SelectedForSharing,
    PlatformInvoked,
    Stored,
}

/** A fixed-size visual reference with a deterministic text fallback. */
data class WalletDemoReviewVisual(
    val imageUri: String? = null,
    val contentDescription: String? = null,
    val fallbackText: String,
)

/** One user-facing or technical labelled value. */
data class WalletDemoReviewValue(
    val label: String,
    val value: String?,
    val supportingText: String? = null,
    val linkUri: String? = null,
) {
    val isVisible: Boolean
        get() = !value.isNullOrBlank()
}

/** A coherent group on an island-specific technical page. */
data class WalletDemoReviewTechnicalSection(
    val id: String,
    val title: String,
    val values: List<WalletDemoReviewValue>,
) {
    val visibleValues: List<WalletDemoReviewValue>
        get() = values.filter(WalletDemoReviewValue::isVisible)
}

/**
 * Container-independent content for one expandable review island.
 *
 * Selection, transaction-code input, consent actions, and transport handles deliberately stay with
 * the surface host. This value model can therefore be rendered in a sheet, full-screen provider
 * surface, or stored-credential destination without importing protocol state into UI components.
 */
data class WalletDemoReviewIsland(
    val id: WalletDemoReviewIslandId,
    val kind: WalletDemoReviewIslandKind,
    val context: WalletDemoReviewSurfaceContext,
    val title: String,
    val subtitle: String? = null,
    val visual: WalletDemoReviewVisual? = null,
    val summaryValues: List<WalletDemoReviewValue> = emptyList(),
    val expandedValues: List<WalletDemoReviewValue> = emptyList(),
    val technicalSections: List<WalletDemoReviewTechnicalSection> = emptyList(),
    /** Reserved for exact typed status facts; Candidate 1 deliberately invents no trust signal. */
    val status: WalletDemoReviewValue? = null,
    val warning: String? = null,
    val initiallyExpanded: Boolean = false,
) {
    val visibleSummaryValues: List<WalletDemoReviewValue>
        get() = summaryValues.filter(WalletDemoReviewValue::isVisible)

    val visibleExpandedValues: List<WalletDemoReviewValue>
        get() = expandedValues.filter(WalletDemoReviewValue::isVisible)

    val visibleTechnicalSections: List<WalletDemoReviewTechnicalSection>
        get() = technicalSections.mapNotNull { section ->
            section.copy(values = section.visibleValues).takeIf { it.values.isNotEmpty() }
        }

    val hasTechnicalDetails: Boolean
        get() = visibleTechnicalSections.isNotEmpty()
}

/** Typed navigation within the current review surface. */
sealed interface WalletDemoReviewRoute {
    data object Summary : WalletDemoReviewRoute

    data class TechnicalDetails(val islandId: WalletDemoReviewIslandId) : WalletDemoReviewRoute
}

/** User decisions that must remain distinct at a review boundary. */
enum class WalletDemoReviewDecision {
    Consent,
    Dismiss,
    Decline,
    Reject,
    Cancel,
}

/** A labelled action whose callback semantics are explicit to its host. */
data class WalletDemoReviewAction(
    val label: String,
    val decision: WalletDemoReviewDecision,
    val enabled: Boolean = true,
)

/** Builds the issuance islands without retaining the issuance session or transaction-code value. */
fun WalletDemoOfferPreview.toReviewIslands(
    context: WalletDemoReviewSurfaceContext = WalletDemoReviewSurfaceContext.Offered,
): List<WalletDemoReviewIsland> = buildList {
    add(issuerReviewIsland(context))
    add(credentialOfferReviewIsland(context))
    informationOfferReviewIsland(context)?.let(::add)
    requiredActionOfferReviewIsland(context)?.let(::add)
}

/** Builds the presentation islands without retaining the preview handle or selection state. */
fun WalletDemoSharingReview.toReviewIslands(
    context: WalletDemoReviewSurfaceContext = WalletDemoReviewSurfaceContext.SelectedForSharing,
): List<WalletDemoReviewIsland> = buildList {
    verifierReviewIsland(context)?.let(::add)
    credentialSharingReviewIsland(context)?.let(::add)
    informationSharingReviewIsland(context)?.let(::add)
    purposeAndTransactionReviewIsland(context)?.let(::add)
}

private fun WalletDemoOfferPreview.issuerReviewIsland(
    context: WalletDemoReviewSurfaceContext,
): WalletDemoReviewIsland {
    val issuerName = issuer.display?.name.presentableOrNull() ?: issuer.credentialIssuer
    return WalletDemoReviewIsland(
        id = WalletDemoReviewIslandId("issuer"),
        kind = WalletDemoReviewIslandKind.Issuer,
        context = context,
        title = issuerName,
        subtitle = "Credential Issuer",
        visual = issuer.display.toReviewVisual(issuerName),
        expandedValues = listOf(
            WalletDemoReviewValue("About", issuer.display?.description),
        ),
        technicalSections = listOf(
            WalletDemoReviewTechnicalSection(
                id = "issuer-identity",
                title = "Issuer identity",
                values = listOf(
                    WalletDemoReviewValue("Credential Issuer", issuer.credentialIssuer, linkUri = issuer.credentialIssuer),
                    WalletDemoReviewValue("Selected display name", issuer.display?.name),
                    WalletDemoReviewValue("Logo source", issuer.display?.logoUri, linkUri = issuer.display?.logoUri),
                ),
            )
        ),
    )
}

private fun WalletDemoOfferPreview.credentialOfferReviewIsland(
    context: WalletDemoReviewSurfaceContext,
): WalletDemoReviewIsland {
    val firstCredential = offeredCredentials.firstOrNull()
    val title = if (offeredCredentials.size == 1 && firstCredential != null) {
        firstCredential.friendlyTitle()
    } else {
        "${offeredCredentials.size} credentials"
    }
    return WalletDemoReviewIsland(
        id = WalletDemoReviewIslandId("credential"),
        kind = WalletDemoReviewIslandKind.Credential,
        context = context,
        title = title,
        subtitle = if (offeredCredentials.size == 1) "Offered credential" else "Offered credentials",
        visual = firstCredential?.display.toReviewVisual(title),
        expandedValues = offeredCredentials.map { credential ->
            WalletDemoReviewValue(
                label = credential.friendlyTitle(),
                value = credential.display?.description ?: "Ready to add",
            )
        },
        technicalSections = offeredCredentials.mapIndexed { index, credential ->
            WalletDemoReviewTechnicalSection(
                id = "credential-$index",
                title = credential.friendlyTitle(),
                values = listOf(
                    WalletDemoReviewValue("Configuration identifier", credential.configurationId),
                    WalletDemoReviewValue("Format", credential.format),
                    WalletDemoReviewValue("Type", credential.vct ?: credential.doctype),
                    WalletDemoReviewValue(
                        "Logo source",
                        credential.display?.logoUri,
                        linkUri = credential.display?.logoUri,
                    ),
                ),
            )
        },
        initiallyExpanded = offeredCredentials.size > 1,
    )
}

private fun WalletDemoOfferPreview.informationOfferReviewIsland(
    context: WalletDemoReviewSurfaceContext,
): WalletDemoReviewIsland? {
    val claims = offeredCredentials.flatMap { credential ->
        credential.claimDisplayGroups().flatMap { group ->
            group.claims.map { claim -> credential to (group.title to claim) }
        }
    }
    if (claims.isEmpty()) return null

    return WalletDemoReviewIsland(
        id = WalletDemoReviewIslandId("information"),
        kind = WalletDemoReviewIslandKind.Information,
        context = context,
        title = "Information",
        subtitle = "${claims.size} ${if (claims.size == 1) "field" else "fields"} supported",
        visual = WalletDemoReviewVisual(fallbackText = "i"),
        expandedValues = claims.map { (_, groupAndClaim) ->
            val (group, claim) = groupAndClaim
            WalletDemoReviewValue(claim.label, claim.inclusion, supportingText = group)
        },
        technicalSections = offeredCredentials.mapIndexedNotNull { index, credential ->
            val values = credential.claims.map { claim ->
                WalletDemoReviewValue(
                    label = claim.displayName
                        ?: CredentialDisplayVocabulary.humanizedClaimLabel(claim.path.lastOrNull().orEmpty()),
                    value = claim.path.joinToString("."),
                    supportingText = if (claim.mandatory == true) "Always included" else "May be included",
                )
            }
            WalletDemoReviewTechnicalSection(
                id = "credential-information-$index",
                title = credential.friendlyTitle(),
                values = values,
            ).takeIf { values.isNotEmpty() }
        },
    )
}

private fun WalletDemoOfferPreview.requiredActionOfferReviewIsland(
    context: WalletDemoReviewSurfaceContext,
): WalletDemoReviewIsland? {
    if (!requiresIssuerAuthentication && transactionCode == null) return null
    val title = when {
        transactionCode != null -> "Transaction code"
        else -> "Issuer sign-in"
    }
    val subtitle = when {
        transactionCode != null -> transactionCode.description ?: "Enter the code provided by the Issuer"
        else -> "Continue securely in your browser"
    }
    return WalletDemoReviewIsland(
        id = WalletDemoReviewIslandId("required-action"),
        kind = WalletDemoReviewIslandKind.RequiredAction,
        context = context,
        title = title,
        subtitle = subtitle,
        visual = WalletDemoReviewVisual(fallbackText = "→"),
        expandedValues = listOfNotNull(
            "Continuing opens your browser to sign in with the Issuer before the credential is added."
                .takeIf { requiresIssuerAuthentication }
                ?.let { WalletDemoReviewValue("Next step", it) },
        ),
        technicalSections = listOf(
            WalletDemoReviewTechnicalSection(
                id = "authorization-method",
                title = "Authorization method",
                values = listOf(
                    WalletDemoReviewValue(
                        "Grant",
                        if (requiresIssuerAuthentication) "Authorization code" else "Pre-authorized code",
                    ),
                    WalletDemoReviewValue("Transaction code input", transactionCode?.inputMode?.name),
                    WalletDemoReviewValue("Expected length", transactionCode?.length?.toString()),
                ),
            )
        ),
        initiallyExpanded = true,
    )
}

private fun WalletDemoSharingReview.verifierReviewIsland(
    context: WalletDemoReviewSurfaceContext,
): WalletDemoReviewIsland? {
    val verifier = request.verifier
    val name = verifier?.display?.name.presentableOrNull()
        ?: verifier?.fallbackName.presentableOrNull()
        ?: verifier?.verifiedOrigin.presentableOrNull()
        ?: "Verifier"
    val verifiedOrigin = verifier?.verifiedOrigin.presentableOrNull()
    val actorValues = buildList {
        if (verifiedOrigin != null && verifiedOrigin != name) {
            add(WalletDemoReviewValue("Verified website", verifiedOrigin))
        }
        verifier?.details.orEmpty().forEach { detail ->
            add(WalletDemoReviewValue(detail.label, detail.value, linkUri = detail.linkUri))
        }
    }
    val technicalSections = buildList {
        WalletDemoReviewTechnicalSection(
            id = "verifier-request",
            title = "Verifier request",
            values = request.technicalDetails.map { detail ->
                WalletDemoReviewValue(detail.label, detail.value, linkUri = detail.linkUri)
            },
        ).takeIf { it.visibleValues.isNotEmpty() }?.let(::add)
        request.readerTrust?.let { trust ->
            add(
                WalletDemoReviewTechnicalSection(
                    id = "reader-authentication",
                    title = "Reader authentication",
                    values = trust.reviewValues(),
                )
            )
        }
        add(
            WalletDemoReviewTechnicalSection(
                id = "response-protection",
                title = "Response protection",
                values = request.responseProtection.reviewValues(),
            )
        )
    }
    val hasVerifierContent = verifier?.hasContent == true
    val hasRequestFacts = technicalSections.any { it.visibleValues.isNotEmpty() }
    if (!hasVerifierContent && !hasRequestFacts) return null

    return WalletDemoReviewIsland(
        id = WalletDemoReviewIslandId("verifier"),
        kind = WalletDemoReviewIslandKind.Verifier,
        context = context,
        title = name,
        subtitle = if (verifiedOrigin == name) "Verified website" else "Verifier",
        visual = verifier?.display.toReviewVisual(name),
        summaryValues = listOf(
            WalletDemoReviewValue("Response", request.responseProtection.summaryText()),
        ),
        expandedValues = actorValues,
        technicalSections = technicalSections,
        initiallyExpanded = verifier?.verifiedOrigin.presentableOrNull() != null ||
            request.readerTrust != null || verifier?.details?.any { it.value.presentableOrNull() != null } == true,
    )
}

private fun WalletDemoSharingReview.credentialSharingReviewIsland(
    context: WalletDemoReviewSurfaceContext,
): WalletDemoReviewIsland? {
    if (credentialOptions.isEmpty()) return null
    val first = credentialOptions.first()
    val title = if (credentialOptions.size == 1) first.label else "${credentialOptions.size} credentials"
    return WalletDemoReviewIsland(
        id = WalletDemoReviewIslandId("credential"),
        kind = WalletDemoReviewIslandKind.Credential,
        context = context,
        title = title,
        subtitle = if (credentialOptions.size == 1) "Selected credential" else "Choose credentials",
        visual = WalletDemoReviewVisual(fallbackText = title.firstOrNull()?.uppercase() ?: "C"),
        expandedValues = credentialOptions.map { option ->
            WalletDemoReviewValue(option.label, option.issuer ?: "Issuer unavailable", supportingText = option.subject)
        },
        technicalSections = credentialOptions.mapIndexed { index, option ->
            WalletDemoReviewTechnicalSection(
                id = "credential-option-$index",
                title = option.label,
                values = listOf(
                    WalletDemoReviewValue("Credential identifier", option.credentialId),
                    WalletDemoReviewValue("Query identifier", option.queryId),
                    WalletDemoReviewValue("Format", option.format),
                    WalletDemoReviewValue("Issuer", option.issuer),
                    WalletDemoReviewValue("Subject", option.subject),
                ),
            )
        },
        initiallyExpanded = true,
    )
}

private fun WalletDemoSharingReview.informationSharingReviewIsland(
    context: WalletDemoReviewSurfaceContext,
): WalletDemoReviewIsland? {
    val disclosures = credentialOptions.flatMap { option -> option.disclosures.map { option to it } }
    if (disclosures.isEmpty()) return null
    val optionalCount = disclosures.count { (_, disclosure) -> disclosure.selectable }
    return WalletDemoReviewIsland(
        id = WalletDemoReviewIslandId("information"),
        kind = WalletDemoReviewIslandKind.Information,
        context = context,
        title = "Information to share",
        subtitle = buildString {
            append(disclosures.size)
            append(if (disclosures.size == 1) " field" else " fields")
            if (optionalCount > 0) append(" · $optionalCount optional")
        },
        visual = WalletDemoReviewVisual(fallbackText = "i"),
        expandedValues = disclosures.map { (option, disclosure) ->
            WalletDemoReviewValue(
                label = disclosure.label,
                value = disclosure.displayValue ?: disclosure.valueJson,
                supportingText = buildString {
                    append(option.label)
                    append(" · ")
                    append(if (disclosure.selectable) "Optional" else "Required")
                },
            )
        },
        technicalSections = credentialOptions.mapIndexedNotNull { index, option ->
            val values = option.disclosures.flatMap { disclosure ->
                listOf(
                    WalletDemoReviewValue(disclosure.label, disclosure.path),
                    WalletDemoReviewValue("Selection", if (disclosure.selectable) "Optional" else "Required"),
                )
            }
            WalletDemoReviewTechnicalSection(
                id = "requested-information-$index",
                title = option.label,
                values = values,
            ).takeIf { values.isNotEmpty() }
        },
        initiallyExpanded = true,
    )
}

private fun WalletDemoSharingReview.purposeAndTransactionReviewIsland(
    context: WalletDemoReviewSurfaceContext,
): WalletDemoReviewIsland? {
    if (request.transactionData.isEmpty()) return null
    val items = request.transactionData.flatMap { group -> group.items.map { group.title to it } }
    return WalletDemoReviewIsland(
        id = WalletDemoReviewIslandId("purpose-and-transaction"),
        kind = WalletDemoReviewIslandKind.PurposeAndTransaction,
        context = context,
        title = request.transactionData.singleOrNull()?.title ?: "Purpose and transaction",
        subtitle = "Review before sharing",
        visual = WalletDemoReviewVisual(fallbackText = "!"),
        expandedValues = items.map { (group, item) ->
            WalletDemoReviewValue(item.label, item.value.reviewText(), supportingText = group)
        },
        technicalSections = request.transactionData.mapIndexed { index, group ->
            WalletDemoReviewTechnicalSection(
                id = "transaction-$index",
                title = group.title,
                values = group.items.map { item ->
                    WalletDemoReviewValue(item.path.id, item.rawValue ?: item.value.reviewText())
                },
            )
        },
        initiallyExpanded = true,
    )
}

private fun WalletDemoMetadataDisplay?.toReviewVisual(fallbackName: String): WalletDemoReviewVisual =
    WalletDemoReviewVisual(
        imageUri = this?.logoUri,
        contentDescription = this?.logoAltText,
        fallbackText = fallbackName.firstOrNull()?.uppercase() ?: "?",
    )

private fun WalletDemoOfferedCredentialMetadata.friendlyTitle(): String =
    CredentialDisplayNameResolver.resolve(
        label = display?.name,
        format = format,
        credentialType = vct ?: doctype ?: configurationId,
    )

private fun String?.presentableOrNull(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun WalletDemoReaderTrust.reviewValues(): List<WalletDemoReviewValue> = when (this) {
    WalletDemoReaderTrust.NotAuthenticated -> listOf(
        WalletDemoReviewValue("Status", "Reader not authenticated"),
    )
    WalletDemoReaderTrust.PendingVerification -> listOf(
        WalletDemoReviewValue("Status", "Verified before sharing"),
    )
    is WalletDemoReaderTrust.Untrusted -> listOf(
        WalletDemoReviewValue("Status", "Reader identity not trusted by this wallet"),
        WalletDemoReviewValue("Reason", reason),
    )
    is WalletDemoReaderTrust.Trusted -> listOf(
        WalletDemoReviewValue("Status", "Trusted reader"),
        WalletDemoReviewValue("Reader identity", readerIdentity),
    )
}

private fun WalletDemoSharingResponseProtection.summaryText(): String = when (this) {
    WalletDemoSharingResponseProtection.None -> "No message-level encryption requested"
    is WalletDemoSharingResponseProtection.Encrypted -> "Protected response"
}

private fun WalletDemoSharingResponseProtection.reviewValues(): List<WalletDemoReviewValue> = when (this) {
    WalletDemoSharingResponseProtection.None -> listOf(
        WalletDemoReviewValue("Message-level encryption", "Not requested"),
    )
    is WalletDemoSharingResponseProtection.Encrypted -> listOf(
        WalletDemoReviewValue("Message-level encryption", "Required"),
        WalletDemoReviewValue("Encryption mechanism", mechanism.displayName),
        WalletDemoReviewValue("Key management algorithm", keyManagementAlgorithm),
        WalletDemoReviewValue("Content encryption algorithm", contentEncryptionAlgorithm),
        WalletDemoReviewValue("Verifier key ID", verifierKeyId),
        WalletDemoReviewValue("Verifier key thumbprint", verifierKeyThumbprint),
    )
}

private val WalletDemoSharingEncryptionMechanism.displayName: String
    get() = when (this) {
        WalletDemoSharingEncryptionMechanism.Jwe -> "JWE encrypted response"
        WalletDemoSharingEncryptionMechanism.DcApiJwt -> "OpenID4VP encrypted response"
        WalletDemoSharingEncryptionMechanism.AnnexCHpke -> "ISO 18013-7 Annex C HPKE"
    }

private fun DisplayValue.reviewText(): String = when (this) {
    is DisplayValue.Text -> value
    is DisplayValue.NumberValue -> value
    is DisplayValue.BooleanValue -> if (value) "Yes" else "No"
    is DisplayValue.DecodedText -> value
    is DisplayValue.Raw -> value
    is DisplayValue.Image -> "$mimeType image"
    is DisplayValue.ListValue -> values.joinToString(", ") { it.reviewText() }
    is DisplayValue.ObjectValue -> entries.joinToString(", ") { "${it.label}: ${it.value.reviewText()}" }
    DisplayValue.NullValue -> "Not provided"
}
