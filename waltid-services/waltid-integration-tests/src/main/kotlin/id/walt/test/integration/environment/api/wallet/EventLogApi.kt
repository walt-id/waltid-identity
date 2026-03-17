@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.environment.api.wallet

import id.walt.commons.testing.E2ETest
import id.walt.test.integration.expectSuccess
import id.walt.webwallet.service.events.EventLogFilterDataResult
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class EventLogApi(private val e2e: E2ETest, private val client: HttpClient) {

    suspend fun listEventLogsRaw(
        walletId: Uuid,
        limit: Int? = null,
        filters: List<String>? = null,
        startingAfter: String? = null,
        sortBy: String? = null,
        sortOrder: String? = null
    ) = client.get("/wallet-api/wallet/$walletId/eventlog") {
        url {
            limit?.let { parameters.append("limit", it.toString()) }
            filters?.forEach { parameters.append("filter", it) }
            startingAfter?.let { parameters.append("startingAfter", it) }
            sortBy?.let { parameters.append("sortBy", it) }
            sortOrder?.let { parameters.append("sortOrder", it) }
        }
    }

    suspend fun listEventLogs(
        walletId: Uuid,
        limit: Int? = null,
        filters: List<String>? = null,
        startingAfter: String? = null,
        sortBy: String? = null,
        sortOrder: String? = null
    ): EventLogFilterDataResult = listEventLogsRaw(walletId, limit, filters, startingAfter, sortBy, sortOrder).let {
        it.expectSuccess()
        it.body<EventLogFilterDataResult>()
    }

    suspend fun deleteEventLogRaw(walletId: Uuid, eventId: Int) =
        client.delete("/wallet-api/wallet/$walletId/eventlog/$eventId")

    suspend fun deleteEventLog(walletId: Uuid, eventId: Int) {
        deleteEventLogRaw(walletId, eventId).expectSuccess()
    }
}
