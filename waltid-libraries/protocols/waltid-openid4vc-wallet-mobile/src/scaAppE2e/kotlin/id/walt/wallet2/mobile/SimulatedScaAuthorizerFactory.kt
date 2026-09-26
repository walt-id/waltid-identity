package id.walt.wallet2.mobile

import id.walt.crypto2.keys.KeyUseAuthorizationException
import id.walt.crypto2.keys.KeyUseAuthorizationFailure
import id.walt.wallet2.handlers.WalletScaPresentationAuthorizer
import id.walt.wallet2.persistence.keys.PlatformManagedKeyProvider
import id.walt.verifier.openid.transactiondata.decodeList
import id.waltid.openid4vp.wallet.presentation.ScaAuthenticationMethods
import kotlinx.serialization.json.*

/** Compiled only by sca-app-e2e.init.gradle into isolated, non-publishable test artifacts.
 * Real app, storage, issuance, consent, signing and transport; ONLY authentication is simulated.
 * These proofs are test data and never evidence of native factors or regulated SCA.
 */
@Suppress("UNUSED_PARAMETER")
internal fun createScaPresentationAuthorizer(provider: PlatformManagedKeyProvider): WalletScaPresentationAuthorizer =
    WalletScaPresentationAuthorizer { _, presentation ->
        val transactions = decodeList(presentation.transactionData)
        if (transactions.any { it.details["payload"]?.jsonObject?.get("transaction_id")?.jsonPrimitive?.content == "sca-app-e2e-denied" }) {
            throw KeyUseAuthorizationException(KeyUseAuthorizationFailure.AuthorizationNotCompleted, "Simulated test authentication cancellation")
        }
        ScaAuthenticationMethods.PossessionAndInherence(
            ScaAuthenticationMethods.Possession.OTHER, ScaAuthenticationMethods.Inherence.OTHER)
    }
