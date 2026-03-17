@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.environment.api.wallet

import id.walt.commons.testing.E2ETest
import id.walt.test.integration.expectSuccess
import id.walt.webwallet.db.models.WalletCredential
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ReportsApi(private val e2e: E2ETest, private val client: HttpClient) {

    suspend fun getFrequentCredentialsRaw(walletId: Uuid, limit: Int? = null) =
        client.get("/wallet-api/wallet/$walletId/reports/frequent/credentials") {
            url {
                limit?.let { parameters.append("limit", it.toString()) }
            }
        }

    suspend fun getFrequentCredentials(walletId: Uuid, limit: Int? = null): List<WalletCredential> =
        getFrequentCredentialsRaw(walletId, limit).let {
            it.expectSuccess()
            it.body<List<WalletCredential>>()
        }
}
