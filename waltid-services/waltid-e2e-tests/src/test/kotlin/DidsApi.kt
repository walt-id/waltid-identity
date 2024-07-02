import E2ETestWebService.test
import id.walt.webwallet.db.models.WalletDid
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.json.JsonObject
import kotlinx.uuid.UUID

class DidsApi(private val client: HttpClient) {
    suspend fun list(wallet: UUID, output: (List<WalletDid>) -> Unit) =
        test("/wallet-api/wallet/{wallet}/dids - list DIDs") {
            client.get("/wallet-api/wallet/$wallet/dids").expectSuccess().apply {
                val dids = body<List<WalletDid>>()
                assert(dids.isNotEmpty()) { "Wallet has no DIDs!" }
                assert(dids.size == 1) { "Wallet has invalid number of DIDs!" }
                output(dids)
            }
        }

    suspend fun get(wallet: UUID, did: String) = test("/wallet-api/wallet/{wallet}/dids/{did} - show specific DID") {
        client.get("/wallet-api/wallet/$wallet/dids/$did").expectSuccess().apply {
            val response = body<JsonObject>()
            println("DID document: $response")
        }
    }
}