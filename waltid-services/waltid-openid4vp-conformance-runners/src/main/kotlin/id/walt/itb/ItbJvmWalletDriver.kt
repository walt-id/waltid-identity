package id.walt.itb

import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.openid4vp.conformance.wallet.WalletCredentialIssuer
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.handlers.WalletScaPresentationAuthorizer
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import io.ktor.client.HttpClient
import io.ktor.http.Url
import java.util.UUID

/** A hosted software wallet cannot attest to an end user's authentication factors. */
internal class ItbAuthenticationUnavailable : IllegalStateException("Per-use authentication is unavailable in this runner")

private val itbHeadlessScaAuthorizer = WalletScaPresentationAuthorizer { _, _ ->
    throw ItbAuthenticationUnavailable()
}

/** Headless execution has no factor source; the callback declines without supplying evidence. */
suspend fun ItbWalletDriver.Companion.create(
    client: HttpClient,
    trustedOrigin: Url,
    clientIdTrust: ClientIdTrustConfiguration,
    authorize: suspend (Url, Url) -> Url,
): ItbWalletDriver {
    val holder = WalletCredentialIssuer().holderCrypto2Key()
    val wallet = Wallet(
        id = "itb-${UUID.randomUUID()}",
        keyStores = listOf(InMemoryKeyStore().apply { addCrypto2Key(holder) }),
        credentialStores = listOf(InMemoryCredentialStore()),
    ).attachKeyAttestationProvider(ItbSyntheticKeyAttester(WalletCredentialIssuer().holderCrypto2Key()))
    return ItbWalletDriver(wallet, client, trustedOrigin, clientIdTrust, authorize, itbHeadlessScaAuthorizer)
}
