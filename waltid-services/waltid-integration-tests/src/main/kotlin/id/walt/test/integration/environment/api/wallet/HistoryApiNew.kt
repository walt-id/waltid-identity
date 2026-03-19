@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.environment.api.wallet

import id.walt.commons.testing.E2ETest
import id.walt.test.integration.expectSuccess
import id.walt.webwallet.db.models.WalletOperationHistory
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class HistoryApi(private val e2e: E2ETest, private val client: HttpClient) {

    suspend fun listHistoryRaw(walletId: Uuid) =
        client.get("/wallet-api/wallet/$walletId/history")

    suspend fun listHistory(walletId: Uuid): List<WalletOperationHistory> =
        listHistoryRaw(walletId).let {
            it.expectSuccess()
            it.body<List<WalletOperationHistory>>()
        }
}
