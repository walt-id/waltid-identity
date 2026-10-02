package id.walt.walletdemo.compose.android

import android.content.Context
import android.content.Intent
import androidx.credentials.ExperimentalDigitalCredentialApi
import androidx.fragment.app.FragmentActivity
import id.walt.wallet2.handlers.WalletIssuanceAuthorization
import id.walt.wallet2.mobile.AndroidDigitalCredentialCreateProvider
import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.MobileWalletCredentialOffer
import id.walt.wallet2.mobile.MobileWalletIssuanceRequest
import id.walt.walletdemo.compose.logic.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Android provider boundary; all protocol validation and issuance behavior remain in the SDK. */
internal interface DigitalCredentialCreateClient {
    data class Offer(val protocol: String, val session: WalletDemoIssuanceSession, val holder: WalletDemoHolderBinding)
    suspend fun prepare(intent: Intent): Offer
    suspend fun accept(id: String, code: String?, selections: List<WalletDemoCredentialSelection>): WalletDemoIssuanceOutcome
    suspend fun authorize(id: String, selections: List<WalletDemoCredentialSelection>): WalletIssuanceAuthorization
    suspend fun continueAuthorization(id: String, callback: String): WalletDemoIssuanceOutcome
    suspend fun cancel(id: String)
    suspend fun resume(id: String): WalletDemoIssuanceOutcome
    suspend fun credentials(): List<WalletDemoCredential>
    suspend fun continuations(): List<WalletDemoDeferredCredential>
}

@OptIn(ExperimentalDigitalCredentialApi::class)
internal class MobileDigitalCredentialCreateClient(
    private val context: Context,
    private val activity: () -> FragmentActivity?,
) : DigitalCredentialCreateClient {
    private var wallet: MobileWallet? = null
    private fun wallet() = requireNotNull(wallet) { "Wallet has not opened" }

    override suspend fun prepare(intent: Intent): DigitalCredentialCreateClient.Offer {
        val allowlist = context.assets.open("privileged_apps.json").bufferedReader().use { it.readText() }
        val input = AndroidDigitalCredentialCreateProvider.extract(intent, allowlist)
        val config = demoWalletConfig()
        val created = createAndroidDemoMobileWallet(context, config, activity)
        wallet = created.wallet
        currentCoroutineContext().ensureActive()
        val identity = created.bootstrap(config.selectedSigningProtection(context))
        currentCoroutineContext().ensureActive()
        val session = created.wallet.startIssuance(MobileWalletIssuanceRequest(
            offer = MobileWalletCredentialOffer.InlineJson(input.request.offerJson), redirectUri = "openid://",
        )).toDemoIssuanceSession()
        return DigitalCredentialCreateClient.Offer(input.request.protocol, session, WalletDemoHolderBinding(identity.keyId, identity.did))
    }

    override suspend fun accept(id: String, code: String?, selections: List<WalletDemoCredentialSelection>) =
        wallet().continuePreAuthorizedIssuance(id, code, selections.toMobileSelections()).toDemoIssuanceOutcome()
    override suspend fun authorize(id: String, selections: List<WalletDemoCredentialSelection>) =
        wallet().beginAuthorizationIssuance(id, selections.toMobileSelections())
    override suspend fun continueAuthorization(id: String, callback: String) =
        wallet().continueAuthorizationIssuance(id, callback).toDemoIssuanceOutcome()
    override suspend fun cancel(id: String) { wallet().cancelIssuance(id) }
    override suspend fun resume(id: String) = wallet().resumeDeferredIssuance(id).toDemoIssuanceOutcome()
    override suspend fun credentials() = wallet().credentials().map { it.toDemoCredential() }
    override suspend fun continuations() = wallet().listDeferredIssuance().map { it.toDemoDeferredCredential() }
}
