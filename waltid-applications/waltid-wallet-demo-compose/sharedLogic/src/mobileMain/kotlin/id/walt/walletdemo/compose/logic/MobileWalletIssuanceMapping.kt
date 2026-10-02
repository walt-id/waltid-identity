package id.walt.walletdemo.compose.logic

import id.walt.wallet2.handlers.WalletIssuanceGrant
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.handlers.WalletIssuanceBatchSession
import id.walt.wallet2.handlers.WalletIssuanceTransactionCode
import id.walt.wallet2.handlers.WalletDeferredCredential
import id.walt.wallet2.handlers.WalletIssuanceContinuation
import id.walt.wallet2.mobile.MobileWalletCredentialSelection
import id.walt.wallet2.mobile.MobileWalletHolderBinding
import id.walt.wallet2.mobile.MobileWalletCredentialHolders

internal fun List<WalletDemoCredentialSelection>.toMobileSelections() = map { selection ->
    MobileWalletCredentialSelection(
        credentialConfigurationId = selection.credentialConfigurationId,
        holders = when (val holders = selection.holders) {
            is WalletDemoCredentialHolders.Existing -> MobileWalletCredentialHolders.Existing(holders.bindings.map { MobileWalletHolderBinding(it.keyId, it.did) })
            is WalletDemoCredentialHolders.NewKeys -> MobileWalletCredentialHolders.NewKeys(holders.count)
        },
    )
}

/** Maps a core issuance session into the demo offer-review model. */
fun WalletIssuanceBatchSession.toDemoIssuanceSession(): WalletDemoIssuanceSession =
    WalletDemoIssuanceSession(
        id = id,
        grant = when (offer.grant) {
            WalletIssuanceGrant.PRE_AUTHORIZED_CODE -> WalletDemoIssuanceGrant.PreAuthorizedCode
            WalletIssuanceGrant.AUTHORIZATION_CODE -> WalletDemoIssuanceGrant.AuthorizationCode
        },
        preview = WalletDemoOfferPreview(
            issuer = WalletDemoIssuerMetadata(
                credentialIssuer = offer.issuer.identifier,
                display = WalletDemoMetadataDisplay(
                    name = offer.issuer.name,
                    logoUri = offer.issuer.logoUri,
                    logoAltText = offer.issuer.logoAltText,
                    description = null,
                ),
            ),
            offeredCredentials = offer.credentials.map { credential ->
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
            transactionCode = offer.transactionCode?.toDemoRequirement(),
            requiresIssuerAuthentication = offer.grant == WalletIssuanceGrant.AUTHORIZATION_CODE,
            batchSize = batchSize,
        ),
    )

internal fun WalletIssuanceOutcome.toDemoIssuanceOutcome(): WalletDemoIssuanceOutcome =
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
            offerConsumed = failure != null || storedCredentialIds.isNotEmpty() || deferredCredentials.isNotEmpty(),
        )
    }

internal fun WalletDeferredCredential.toDemoDeferredCredential() = WalletIssuanceContinuation(this).toDemoDeferredCredential()

internal fun WalletIssuanceContinuation.toDemoDeferredCredential() = WalletDemoDeferredCredential(
    id = id,
    credentialConfigurationId = credentialConfigurationId,
    credentialIdentifier = credentialIdentifier,
    intervalSeconds = intervalSeconds,
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
