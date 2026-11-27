package id.walt.walletdemo.compose.logic

import id.walt.wallet2.handlers.WalletIssuanceOfferPreview
import id.walt.wallet2.handlers.WalletIssuanceGrant
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.mobile.issuance.IssuanceSession
import id.walt.wallet2.handlers.WalletIssuanceTransactionCode
import id.walt.wallet2.handlers.WalletDeferredCredential
import id.walt.wallet2.handlers.WalletIssuanceContinuation
import id.walt.wallet2.handlers.WalletIssuanceContinuationStatus
import id.walt.wallet2.handlers.WalletIssuanceErrorCode
import id.walt.wallet2.mobile.issuance.IssuanceCredentialSelection
import id.walt.wallet2.mobile.issuance.IssuanceHolderBinding
import id.walt.wallet2.mobile.issuance.IssuanceHolders

/** Keeps in-app and platform-provider acceptance on the same SDK batch-selection contract. */
fun List<WalletDemoCredentialSelection>.toIssuanceSelections(): List<IssuanceCredentialSelection> = map { selection ->
    IssuanceCredentialSelection(
        credentialConfigurationId = selection.credentialConfigurationId,
        holders = when (val holders = selection.holders) {
            is WalletDemoCredentialHolders.Existing -> IssuanceHolders.Existing(holders.bindings.map { IssuanceHolderBinding(it.keyId, it.did) })
            is WalletDemoCredentialHolders.NewKeys -> IssuanceHolders.NewKeys(holders.count)
        },
    )
}

/** Maps a core issuance session into the demo offer-review model. */
fun IssuanceSession.toDemoIssuanceSession(): WalletDemoIssuanceSession =
    offer.toDemoIssuanceSession(id.value, batchSize, credentialDisplayMetadata)

internal fun WalletIssuanceOfferPreview.toDemoIssuanceSession(
    sessionId: String, batchSize: Int?, credentialDisplayMetadata: Map<String, String>,
): WalletDemoIssuanceSession =
    WalletDemoIssuanceSession(
        id = sessionId,
        grant = when (grant) {
            WalletIssuanceGrant.PRE_AUTHORIZED_CODE -> WalletDemoIssuanceGrant.PreAuthorizedCode
            WalletIssuanceGrant.AUTHORIZATION_CODE -> WalletDemoIssuanceGrant.AuthorizationCode
        },
        preview = WalletDemoOfferPreview(
            issuer = WalletDemoIssuerMetadata(
                credentialIssuer = issuer.identifier,
                display = WalletDemoMetadataDisplay(
                    name = issuer.name,
                    logoUri = issuer.logoUri,
                    logoAltText = issuer.logoAltText,
                    description = null,
                ),
            ),
            offeredCredentials = credentials.map { credential ->
                WalletDemoOfferedCredentialMetadata(
                    configurationId = credential.configurationId,
                    format = credential.format,
                    vct = credential.vct,
                    doctype = credential.doctype,
                    display = WalletDemoMetadataDisplay(
                        name = credential.name,
                        logoUri = credential.logoUri,
                        logoAltText = credential.logoAltText,
                        description = credential.descriptionText,
                        backgroundColor = credential.backgroundColor,
                        backgroundImageUri = credential.backgroundImageUri,
                        textColor = credential.textColor,
                    ),
                    claims = StoredCredentialMetadataParser.claims(credentialDisplayMetadata[credential.configurationId], platformPreferredLocales()),
                )
            },
            transactionCode = transactionCode?.toDemoRequirement(),
            requiresIssuerAuthentication = grant == WalletIssuanceGrant.AUTHORIZATION_CODE,
            batchSize = batchSize,
        ),
    )

fun WalletIssuanceOutcome.toDemoIssuanceOutcome(): WalletDemoIssuanceOutcome =
    when (this) {
        is WalletIssuanceOutcome.Stored -> WalletDemoIssuanceOutcome.Stored(credentialIds)
        is WalletIssuanceOutcome.Deferred -> WalletDemoIssuanceOutcome.Deferred(
            storedCredentialIds = storedCredentialIds,
            credentials = credentials.map { it.toDemoDeferredCredential() },
        )
        is WalletIssuanceOutcome.Cancelled -> WalletDemoIssuanceOutcome.Cancelled
        is WalletIssuanceOutcome.Failed -> WalletDemoIssuanceOutcome.Failed(
            message = error.message,
            storedCredentialIds = storedCredentialIds,
            deferredCredentials = deferredCredentials.map { it.toDemoDeferredCredential() },
            failedTargetCount = if (failure == null) 0 else 1,
            notAttemptedTargetCount = failure?.notAttempted?.size ?: 0,
            offerConsumed = !error.canRetryOffer,
            kind = when (error.code) {
                WalletIssuanceErrorCode.REMOTE_OUTCOME_UNCERTAIN -> WalletDemoIssuanceFailureKind.RemoteOutcomeUncertain
                WalletIssuanceErrorCode.STORAGE_OUTCOME_UNCERTAIN -> WalletDemoIssuanceFailureKind.StorageOutcomeUncertain
                else -> WalletDemoIssuanceFailureKind.General
            },
        )
    }

internal fun WalletDeferredCredential.toDemoDeferredCredential() = WalletIssuanceContinuation(this).toDemoDeferredCredential()

fun WalletIssuanceContinuation.toDemoDeferredCredential() = WalletDemoDeferredCredential(
    id = id,
    credentialConfigurationId = credentialConfigurationId,
    credentialIdentifier = credentialIdentifier,
    intervalSeconds = intervalSeconds,
    status = when (status) {
        WalletIssuanceContinuationStatus.UNRESOLVED -> WalletDemoContinuationStatus.Unresolved
        WalletIssuanceContinuationStatus.AWAITING_ISSUER -> WalletDemoContinuationStatus.AwaitingIssuer
        WalletIssuanceContinuationStatus.AWAITING_LOCAL_SAVE -> WalletDemoContinuationStatus.AwaitingLocalSave
        WalletIssuanceContinuationStatus.REMOTE_OUTCOME_UNCERTAIN -> WalletDemoContinuationStatus.RemoteOutcomeUncertain
        WalletIssuanceContinuationStatus.STORAGE_OUTCOME_UNCERTAIN -> WalletDemoContinuationStatus.StorageOutcomeUncertain
    },
    displayMetadataJson = displayMetadataJson,
)

private fun WalletIssuanceTransactionCode.toDemoRequirement(): WalletDemoTransactionCodeRequirement =
    WalletDemoTransactionCodeRequirement(
        inputMode = when (inputMode ?: "numeric") {
            "numeric" -> WalletDemoTransactionCodeInputMode.Numeric
            "text" -> WalletDemoTransactionCodeInputMode.Text
            else -> throw IllegalArgumentException("Unsupported transaction code input mode: $inputMode")
        },
        length = length,
        description = descriptionText,
    )
