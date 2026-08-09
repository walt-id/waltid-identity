package id.walt.walletdemo.compose.logic

/**
 * Everything the user reviews before one presentation is shared, independent of the transport that
 * delivered the request.
 *
 * This is deliberately a demo/UI-layer model rather than a protocol abstraction. It carries no
 * preview or session handle, no raw authorization request, no Annex C DeviceRequest, no encryption
 * input and no platform objects: the flow owner keeps those and submits with them. Without that
 * boundary the review UI would grow into a second protocol implementation, and every transport added
 * later would have to pretend to be an OpenID4VP preview to be renderable.
 *
 * @property request What is worth reviewing about who is asking and how the answer is protected.
 * @property credentialOptions Wallet credentials that can satisfy the request.
 * @property credentialRequirements Credential-query combinations that must be satisfied to submit.
 */
data class WalletDemoSharingReview(
    val request: WalletDemoSharingRequest,
    val credentialOptions: List<WalletDemoPresentationCredentialOption>,
    val credentialRequirements: List<WalletDemoPresentationCredentialRequirement> = emptyList(),
)

/**
 * Request metadata broken into concepts instead of protocol fields.
 *
 * Each transport supplies only the concepts it actually has, and an absent concept stays null or
 * empty so the UI can omit it. Filling an absent concept with a placeholder would claim the request
 * said something it never said - a fabricated `client_id` for an unsigned Digital Credentials
 * request, or an OpenID4VP `state` for an Annex C DeviceRequest.
 *
 * @property requester Who is asking, or null when the transport proves nothing about the caller.
 * @property readerTrust Reader-authentication state, or null when the protocol has no reader auth.
 * @property responseProtection Protection applied to the response the wallet is about to produce.
 * @property transactionData Transactions this presentation will authorize, already display-grouped.
 * @property technicalDetails Protocol-level values shown behind a disclosure for inspection.
 */
data class WalletDemoSharingRequest(
    val requester: WalletDemoSharingRequester?,
    val readerTrust: WalletDemoReaderTrust? = null,
    val responseProtection: WalletDemoSharingResponseProtection = WalletDemoSharingResponseProtection.None,
    val transactionData: List<ClaimGroup> = emptyList(),
    val technicalDetails: List<WalletDemoSharingDetail> = emptyList(),
)

/**
 * The identity shown to the user for the party requesting the presentation.
 *
 * [verifiedOrigin] is separate from [display] because they have different weight: display metadata is
 * self-asserted by the request, while a verified origin was authenticated by the platform or by
 * request signing. A wallet that renders them identically invites the user to trust the wrong one.
 *
 * @property display Self-asserted requester display metadata, when the request carried any.
 * @property fallbackName Name to show when [display] has none.
 * @property verifiedOrigin Origin authenticated outside the request itself, when there is one.
 * @property details Additional requester-published links, such as privacy policy or terms.
 */
data class WalletDemoSharingRequester(
    val display: WalletDemoMetadataDisplay? = null,
    val fallbackName: String? = null,
    val verifiedOrigin: String? = null,
    val details: List<WalletDemoSharingDetail> = emptyList(),
) {
    internal val hasContent: Boolean
        get() = !display?.name.isNullOrBlank() ||
            !fallbackName.isNullOrBlank() ||
            !verifiedOrigin.isNullOrBlank() ||
            details.any { !it.value.isNullOrBlank() }
}

/** One labelled value in a requester or technical-details list. */
data class WalletDemoSharingDetail(
    val label: String,
    val value: String?,
    val linkUri: String? = null,
)

/**
 * Reader-authentication state as the user needs to understand it.
 *
 * A protocol without reader authentication is represented by a null reader trust rather than by a
 * state here, so no section is rendered at all: an OpenID4VP Digital Credentials request has no
 * reader to be trusted or untrusted, and showing a reassuring or alarming reader row for it would be
 * a statement about something the request does not contain.
 *
 * The states that do exist all describe a request the wallet is still willing to process. A request
 * whose reader authentication fails cryptographic verification is rejected before any preview, so
 * none of these means "bad signature".
 */
sealed interface WalletDemoReaderTrust {
    /** The protocol supports reader authentication but the request carried none. */
    data object NotAuthenticated : WalletDemoReaderTrust

    /**
     * Reader authentication has not been checked yet because the raw request is withheld until the
     * user consents. It is verified before any credential data leaves the wallet.
     */
    data object PendingVerification : WalletDemoReaderTrust

    /**
     * The reader's signature verified, but no trust policy accepts the reader.
     *
     * @property reason Why the trust policy did not accept the reader.
     */
    data class Untrusted(val reason: String) : WalletDemoReaderTrust

    /**
     * Reader authentication verified and a trust policy accepted the reader.
     *
     * @property readerIdentity Certificate subject or comparable reader identity to show.
     */
    data class Trusted(val readerIdentity: String) : WalletDemoReaderTrust
}

/** Protection applied to the response the wallet is about to produce. */
sealed interface WalletDemoSharingResponseProtection {
    /** The response is returned without message-level encryption. */
    data object None : WalletDemoSharingResponseProtection

    /**
     * The response is encrypted to a key the request supplied.
     *
     * The algorithm properties are nullable because transports differ in what they publish before
     * consent: OpenID4VP names the JWE algorithms in verifier metadata, while a Digital Credentials
     * request states only its response mode.
     *
     * @property mechanism Which encryption scheme the transport applies.
     * @property keyManagementAlgorithm JWE `alg`, when the request published it.
     * @property contentEncryptionAlgorithm JWE `enc`, when the request published it.
     * @property verifierKeyId Verifier key identifier, when the request published one.
     * @property verifierKeyThumbprint Thumbprint of the key the response is encrypted to.
     */
    data class Encrypted(
        val mechanism: WalletDemoSharingEncryptionMechanism,
        val keyManagementAlgorithm: String? = null,
        val contentEncryptionAlgorithm: String? = null,
        val verifierKeyId: String? = null,
        val verifierKeyThumbprint: String? = null,
    ) : WalletDemoSharingResponseProtection
}

/** Encryption schemes the demo's transports can apply to a response. */
enum class WalletDemoSharingEncryptionMechanism {
    /** OpenID4VP encrypted response returned over a verifier response URI. */
    Jwe,

    /** OpenID4VP Digital Credentials API `dc_api.jwt` response mode. */
    DcApiJwt,

    /** ISO 18013-7 Annex C HPKE session encryption. */
    AnnexCHpke,
}

/**
 * Credential and disclosure choices made in a sharing review.
 *
 * Kept apart from the review itself so a transport can hold selection state in whatever lifecycle it
 * owns - a wallet-task state machine, or an Activity the operating system started - while the
 * selection *rules* stay in one place.
 */
data class WalletDemoSharingSelection(
    val credentials: Set<WalletDemoPresentationCredentialSelection> = emptySet(),
    val disclosures: Set<WalletDemoPresentationDisclosureSelection> = emptySet(),
)

/**
 * Applies a credential toggle.
 *
 * Selecting a credential for a query that does not allow multiple matches replaces that query's
 * previous choice, and drops the disclosures selected for the credential that is no longer chosen -
 * otherwise a disclosure the user approved for one credential would silently travel with another.
 *
 * @param selection The credential the user toggled.
 * @param option The matching option, used only for its `multiple` flag.
 */
fun WalletDemoSharingSelection.toggleCredential(
    selection: WalletDemoPresentationCredentialSelection,
    option: WalletDemoPresentationCredentialOption?,
): WalletDemoSharingSelection {
    val nextCredentials = when {
        selection in credentials -> credentials - selection
        option?.multiple == true -> credentials + selection
        else -> credentials.filterNot { it.queryId == selection.queryId }.toSet() + selection
    }
    val retainedDisclosures = if (option?.multiple == true) {
        disclosures.filterNot { it.queryId == selection.queryId && it.credentialId == selection.credentialId }
    } else {
        disclosures.filterNot { it.queryId == selection.queryId }
    }.toSet()

    return WalletDemoSharingSelection(
        credentials = nextCredentials,
        disclosures = retainedDisclosures.forSelectedCredentials(nextCredentials),
    )
}

/** Applies a disclosure toggle, keeping only disclosures that belong to a selected credential. */
fun WalletDemoSharingSelection.toggleDisclosure(
    selection: WalletDemoPresentationDisclosureSelection,
): WalletDemoSharingSelection = copy(
    disclosures = (if (selection in disclosures) disclosures - selection else disclosures + selection)
        .forSelectedCredentials(credentials),
)

/** The selection a review opens with: one credential per query needed to satisfy the request. */
fun WalletDemoSharingReview.defaultCredentialSelection(): Set<WalletDemoPresentationCredentialSelection> =
    defaultCredentialSelection(credentialOptions, credentialRequirements)

/** Whether [selectedCredentialOptions] satisfies every credential requirement exactly once. */
fun WalletDemoSharingReview.hasCompleteCredentialSelection(
    selectedCredentialOptions: Set<WalletDemoPresentationCredentialSelection>,
): Boolean = hasCompleteCredentialSelection(credentialOptions, credentialRequirements, selectedCredentialOptions)

/**
 * Maps a normal OpenID4VP preview onto the shared review model.
 *
 * The dependency direction matters more than the mapping does: the protocol preview is translated
 * into the review model, never the other way round. A transport that instead impersonated an
 * OpenID4VP preview would have to invent the fields it has no answer for.
 */
fun WalletDemoPresentationPreview.toSharingReview(): WalletDemoSharingReview = WalletDemoSharingReview(
    request = toSharingRequest(),
    credentialOptions = credentialOptions,
    credentialRequirements = credentialRequirements,
)

/** Maps the request metadata of a normal OpenID4VP preview or protocol error onto review concepts. */
fun WalletDemoPresentationRequestInfo.toSharingRequest(): WalletDemoSharingRequest = WalletDemoSharingRequest(
    requester = verifierMetadata?.let { metadata ->
        WalletDemoSharingRequester(
            display = metadata.display,
            details = listOf(
                WalletDemoSharingDetail("Client URI", metadata.clientUri, metadata.clientUri),
                WalletDemoSharingDetail("Privacy policy", metadata.policyUri, metadata.policyUri),
                WalletDemoSharingDetail("Terms of service", metadata.termsOfServiceUri, metadata.termsOfServiceUri),
            ),
        )
    }?.takeIf { it.hasContent },
    // Plain OpenID4VP has no reader authentication concept, so no reader-trust section is offered.
    readerTrust = null,
    responseProtection = when (val encryption = responseEncryption) {
        WalletDemoResponseEncryption.NotRequired -> WalletDemoSharingResponseProtection.None
        is WalletDemoResponseEncryption.Required -> WalletDemoSharingResponseProtection.Encrypted(
            mechanism = WalletDemoSharingEncryptionMechanism.Jwe,
            keyManagementAlgorithm = encryption.keyManagementAlgorithm,
            contentEncryptionAlgorithm = encryption.contentEncryptionAlgorithm,
            verifierKeyId = encryption.verifierKeyId,
            verifierKeyThumbprint = encryption.verifierKeyThumbprint,
        )
    },
    transactionData = transactionData,
    technicalDetails = listOf(
        WalletDemoSharingDetail("Client ID", clientId),
        WalletDemoSharingDetail("Response URI", responseUri),
        WalletDemoSharingDetail("State", state),
        WalletDemoSharingDetail("Nonce", nonce),
    ),
)

internal fun defaultCredentialSelection(
    credentialOptions: List<WalletDemoPresentationCredentialOption>,
    credentialRequirements: List<WalletDemoPresentationCredentialRequirement>,
): Set<WalletDemoPresentationCredentialSelection> {
    val firstSelectionByQuery = credentialOptions
        .groupBy { it.queryId }
        .mapValues { (_, options) -> options.first().selection }
    if (firstSelectionByQuery.isEmpty()) return emptySet()
    if (credentialRequirements.isEmpty()) return setOf(firstSelectionByQuery.values.first())

    val selectedQueryIds = linkedSetOf<String>()
    credentialRequirements.forEach { requirement ->
        val queryIds = requirement.options.firstOrNull { option ->
            option.isNotEmpty() && option.all { queryId -> queryId in firstSelectionByQuery }
        }
            ?: requirement.options.firstOrNull()
                ?.filter { queryId -> queryId in firstSelectionByQuery }
        queryIds?.let { selectedQueryIds += it }
    }
    return selectedQueryIds
        .mapNotNull { queryId -> firstSelectionByQuery[queryId] }
        .toSet()
}

internal fun hasCompleteCredentialSelection(
    credentialOptions: List<WalletDemoPresentationCredentialOption>,
    credentialRequirements: List<WalletDemoPresentationCredentialRequirement>,
    selectedCredentialOptions: Set<WalletDemoPresentationCredentialSelection>,
): Boolean {
    val optionBySelection = credentialOptions.associateBy { it.selection }
    val selectedOptions = selectedCredentialOptions.mapNotNull { selection -> optionBySelection[selection] }
    if (selectedOptions.isEmpty()) return false
    val selectedCountsByQuery = selectedOptions.groupingBy { it.queryId }.eachCount()
    if (selectedOptions.any { option -> selectedCountsByQuery.getValue(option.queryId) > 1 && !option.multiple }) {
        return false
    }

    val selectedQueryIds = selectedOptions
        .map { it.queryId }
        .toSet()

    if (credentialRequirements.isEmpty()) return true
    return credentialRequirements.all { requirement ->
        requirement.options.any { option ->
            option.isNotEmpty() && option.all { queryId -> queryId in selectedQueryIds }
        }
    }
}
