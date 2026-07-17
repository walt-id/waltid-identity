package id.walt.wallet2.handlers

import id.walt.credentials.CredentialParser
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Serializable
data class ImportCredentialRequest(
    val rawCredential: String,
    val label: String? = null,
)

object WalletCredentialHandler {
    suspend fun importCredential(wallet: Wallet, request: ImportCredentialRequest): StoredCredential {
        val (_, credential) = CredentialParser.detectAndParse(request.rawCredential)
        return StoredCredential(
            id = Uuid.random().toString(),
            credential = credential,
            label = request.label,
            addedAt = Clock.System.now(),
        ).also { wallet.addCredential(it) }
    }
}
