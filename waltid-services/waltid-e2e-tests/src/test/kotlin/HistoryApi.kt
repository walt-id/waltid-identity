import E2ETestWebService.test
import id.walt.webwallet.db.models.WalletOperationHistory
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.uuid.UUID

class HistoryApi(private val client: HttpClient) {
    suspend fun list(wallet: UUID, output: ((List<WalletOperationHistory>) -> Unit)? = null) =
        test("/wallet-api/wallet/{wallet}/history - get operation history") {
            client.get("/wallet-api/wallet/$wallet/history").expectSuccess().apply {
                output?.invoke(body<List<WalletOperationHistory>>())
            }
        }
}