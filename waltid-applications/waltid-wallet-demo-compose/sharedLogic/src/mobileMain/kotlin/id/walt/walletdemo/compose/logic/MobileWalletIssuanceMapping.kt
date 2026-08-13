package id.walt.walletdemo.compose.logic

import id.walt.wallet2.handlers.WalletIssuanceGrant
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.handlers.WalletIssuanceSession
import id.walt.wallet2.handlers.WalletIssuanceTransactionCode

/** Maps a core issuance session into the demo offer-review model. */
fun WalletIssuanceSession.toDemoIssuanceSession(): WalletDemoIssuanceSession =
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
                    vct = null,
                    doctype = null,
                    display = WalletDemoMetadataDisplay(
                        name = credential.name,
                        logoUri = credential.logoUri,
                        logoAltText = null,
                        description = credential.descriptionText,
                    ),
                    claims = emptyList(),
                )
            },
            transactionCode = offer.transactionCode?.toDemoRequirement(),
            requiresIssuerAuthentication = offer.grant == WalletIssuanceGrant.AUTHORIZATION_CODE,
        ),
    )

internal fun WalletIssuanceOutcome.toDemoIssuanceOutcome(): WalletDemoIssuanceOutcome =
    when (this) {
        is WalletIssuanceOutcome.Stored -> WalletDemoIssuanceOutcome.Stored(credentialIds)
        is WalletIssuanceOutcome.Deferred -> WalletDemoIssuanceOutcome.Deferred(
            storedCredentialIds = storedCredentialIds,
            credentials = credentials.map { deferred ->
                WalletDemoDeferredCredential(
                    id = deferred.id,
                    credentialConfigurationId = deferred.credentialConfigurationId,
                    intervalSeconds = deferred.intervalSeconds,
                )
            },
        )
        is WalletIssuanceOutcome.Cancelled -> WalletDemoIssuanceOutcome.Cancelled
        is WalletIssuanceOutcome.Failed -> WalletDemoIssuanceOutcome.Failed(error.message)
    }

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
