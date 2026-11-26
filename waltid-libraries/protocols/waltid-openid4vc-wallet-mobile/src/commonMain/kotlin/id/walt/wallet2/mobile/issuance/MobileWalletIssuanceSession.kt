package id.walt.wallet2.mobile.issuance

import id.walt.wallet2.handlers.WalletIssuanceAuthorization
import id.walt.wallet2.handlers.WalletIssuanceBatchSession
import id.walt.wallet2.handlers.WalletIssuanceGrant
import id.walt.wallet2.handlers.WalletIssuanceOfferPreview
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.mobile.MobileWallet

/** Durable reference for reopening or cancelling an issuance offer.
 * @property value Stable reference issued by this wallet.
 */
public data class MobileWalletIssuanceSessionId(public val value: String)

/** Durable reference saved alongside a browser launch for callback delivery after restart.
 * @property value Opaque reference bound to the browser authorization.
 */
public data class MobileWalletAuthorizationId(public val value: String)

/** Reviewed offer with the one grant the wallet selected during discovery. */
public sealed class MobileWalletIssuanceSession private constructor(
    internal val snapshot: WalletIssuanceBatchSession,
) {
    /** Stable reference to this wallet's retained offer. */
    public val id: MobileWalletIssuanceSessionId = MobileWalletIssuanceSessionId(snapshot.id)
    /** Issuer and credentials to show before acceptance. */
    public val offer: WalletIssuanceOfferPreview get() = snapshot.offer
    /** Maximum number of instances advertised by the issuer. */
    public val batchSize: Int? get() = snapshot.batchSize
    /** Localized display metadata by credential configuration. */
    public val credentialDisplayMetadata: Map<String, String> get() = snapshot.credentialDisplayMetadata

    /** Browser authorization becomes available after the offer review. */
    public class AuthorizationCode internal constructor(
        snapshot: WalletIssuanceBatchSession, private val wallet: MobileWallet,
    ) : MobileWalletIssuanceSession(snapshot) {
        /** Accept the selected credentials and prepare a browser request for this app registration. */
        public suspend fun beginAuthorization(
            registration: IssuanceClientRegistration,
            credentials: List<MobileWalletCredentialSelection>? = null,
        ): MobileWalletAuthorization = MobileWalletAuthorization(
            MobileWalletAuthorizationId(id.value), wallet.beginAuthorizationIssuance(id.value, registration, credentials), wallet,
        )
    }

    /** Pre-authorized issuance may require a separately delivered transaction code or client identity. */
    public class PreAuthorized internal constructor(
        snapshot: WalletIssuanceBatchSession, private val wallet: MobileWallet,
    ) : MobileWalletIssuanceSession(snapshot) {
        /** Accepts this offer. Supply [clientId] when the issuer requires an identified wallet. */
        public suspend fun accept(
            transactionCode: String? = null,
            credentials: List<MobileWalletCredentialSelection>? = null,
            clientId: String? = null,
        ): WalletIssuanceOutcome = wallet.continuePreAuthorizedIssuance(id.value, transactionCode, credentials, clientId)
    }

    internal companion object {
        fun from(snapshot: WalletIssuanceBatchSession, wallet: MobileWallet): MobileWalletIssuanceSession =
            when (snapshot.offer.grant) {
                WalletIssuanceGrant.AUTHORIZATION_CODE -> AuthorizationCode(snapshot, wallet)
                WalletIssuanceGrant.PRE_AUTHORIZED_CODE -> PreAuthorized(snapshot, wallet)
            }
    }
}

/** Browser request and its wallet-bound callback continuation. Save [id] before opening [request]. */
public class MobileWalletAuthorization internal constructor(
    /** Durable reference to persist before opening the browser. */
    public val id: MobileWalletAuthorizationId,
    /** Browser request to launch for this authorization. */
    public val request: WalletIssuanceAuthorization,
    private val wallet: MobileWallet,
) {
    /** Validates the returned callback and continues issuance. */
    public suspend fun complete(callbackUri: String): WalletIssuanceOutcome =
        wallet.continueAuthorizationIssuance(id, callbackUri)
}
