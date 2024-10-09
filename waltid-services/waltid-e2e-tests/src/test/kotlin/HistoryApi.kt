@file:OptIn(ExperimentalUuidApi::class)

import id.walt.commons.testing.E2ETest.test
import id.walt.webwallet.db.models.WalletOperationHistory
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


class HistoryApi(private val client: HttpClient) {
    suspend fun list(wallet: Uuid, output: ((List<WalletOperationHistory>) -> Unit)? = null) =
        test("/wallet-api/wallet/{wallet}/history - get operation history") {
            client.get("/wallet-api/wallet/$wallet/history").expectSuccess().apply {
                output?.invoke(body<List<WalletOperationHistory>>())
            }
        }
}
