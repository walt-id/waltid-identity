@file:OptIn(ExperimentalUuidApi::class)

import id.walt.commons.testing.E2ETest
import id.walt.webwallet.db.models.WalletOperationHistory
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


class HistoryApi(private val e2e: E2ETest, private val client: HttpClient) {
    suspend fun list(wallet: Uuid, output: ((List<WalletOperationHistory>) -> Unit)? = null) =
        e2e.test("/wallet-api/wallet/{wallet}/history - get operation history") {
            client.get("/wallet-api/wallet/$wallet/history").expectSuccess().apply {
                output?.invoke(body<List<WalletOperationHistory>>())
            }
        }
}
