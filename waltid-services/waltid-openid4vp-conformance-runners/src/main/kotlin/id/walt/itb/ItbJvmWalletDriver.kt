package id.walt.itb

import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.openid4vp.conformance.wallet.WalletCredentialIssuer
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import io.ktor.client.HttpClient
import io.ktor.http.Url
import java.util.UUID

/** Headless execution deliberately has no authentication-factor provider. */
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
    )
    return ItbWalletDriver(wallet, client, trustedOrigin, clientIdTrust, authorize)
}
