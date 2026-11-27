package id.walt.walletdemo.compose.android

import id.walt.wallet2.mobile.issuance.DeferredIssuanceId
import id.walt.wallet2.mobile.issuance.IssuanceSession
import id.walt.wallet2.mobile.issuance.IssuanceAuthorizationId
import id.walt.wallet2.mobile.issuance.IssuanceSessionId
import android.content.Context
import android.content.Intent
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.fragment.app.FragmentActivity
import id.walt.wallet2.handlers.WalletIssuanceAuthorization
import id.walt.wallet2.mobile.digitalcredentials.AndroidDigitalCredentialCreateProvider
import id.walt.wallet2.mobile.Wallet
import id.walt.wallet2.mobile.issuance.IssuanceClientRegistration
import id.walt.wallet2.mobile.issuance.CredentialOfferInput
import id.walt.wallet2.mobile.issuance.IssuanceRequest
import id.walt.walletdemo.compose.logic.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Android provider boundary; all protocol validation and issuance behavior remain in the SDK. */
internal interface DigitalCredentialCreateClient {
    data class Offer(val protocol: String, val session: WalletDemoIssuanceSession, val holder: WalletDemoHolderBinding)
    data class Authorization(val id: IssuanceAuthorizationId, val request: WalletIssuanceAuthorization)
    suspend fun prepare(intent: Intent): Offer
    suspend fun accept(id: String, code: String?, selections: List<WalletDemoCredentialSelection>): WalletDemoIssuanceOutcome
    suspend fun authorize(id: String, selections: List<WalletDemoCredentialSelection>): Authorization
    suspend fun completeAuthorization(id: IssuanceAuthorizationId, callback: String): WalletDemoIssuanceOutcome
    suspend fun cancel(id: String)
    suspend fun resume(id: String): WalletDemoIssuanceOutcome
    suspend fun credentials(): List<WalletDemoCredential>
    suspend fun continuations(): List<WalletDemoDeferredCredential>
}

@OptIn(ExperimentalDigitalCredentialApi::class)
internal class WalletDigitalCredentialCreateClient(
    private val context: Context,
    private val activity: () -> FragmentActivity?,
) : DigitalCredentialCreateClient {
    private var wallet: Wallet? = null
    private fun wallet() = requireNotNull(wallet) { "Wallet has not opened" }

    override suspend fun prepare(intent: Intent): DigitalCredentialCreateClient.Offer {
        val allowlist = context.assets.open("privileged_apps.json").bufferedReader().use { it.readText() }
        val input = AndroidDigitalCredentialCreateProvider.extract(intent, allowlist)
        val config = demoWalletConfig()
        val created = openAndroidDemoWallet(context, config, activity)
        wallet = created.wallet
        currentCoroutineContext().ensureActive()
        val identity = created.bootstrap(config.selectedSigningProtection(context))
        currentCoroutineContext().ensureActive()
        val session = created.wallet.startIssuance(IssuanceRequest(
            offer = CredentialOfferInput.InlineJson(input.request.offerJson),
        )).toDemoIssuanceSession()
        return DigitalCredentialCreateClient.Offer(input.request.protocol, session, WalletDemoHolderBinding(identity.keyId, identity.did))
    }

    override suspend fun accept(id: String, code: String?, selections: List<WalletDemoCredentialSelection>) =
        (requireNotNull(wallet().resumeIssuance(IssuanceSessionId(id))) as IssuanceSession.PreAuthorized)
            .accept(code, selections.toIssuanceSelections(), clientId = "eudiw-abca").toDemoIssuanceOutcome()
    override suspend fun authorize(id: String, selections: List<WalletDemoCredentialSelection>) =
        (requireNotNull(wallet().resumeIssuance(IssuanceSessionId(id))) as IssuanceSession.AuthorizationCode)
            .beginAuthorization(IssuanceClientRegistration("eudiw-abca", DigitalCredentialCreateAuthHandoff.REDIRECT_URI), selections.toIssuanceSelections())
            .let { pending -> DigitalCredentialCreateClient.Authorization(pending.id, pending.request) }
    override suspend fun completeAuthorization(id: IssuanceAuthorizationId, callback: String) =
        wallet().completeAuthorization(id, callback).toDemoIssuanceOutcome()
    override suspend fun cancel(id: String) { wallet().cancelIssuance(IssuanceSessionId(id)) }
    override suspend fun resume(id: String) = wallet().resumeDeferredIssuance(DeferredIssuanceId(id)).toDemoIssuanceOutcome()
    override suspend fun credentials() = wallet().credentials().map { it.toDemoCredential() }
    override suspend fun continuations() = wallet().listDeferredIssuance().map { it.toDemoDeferredCredential() }
}
