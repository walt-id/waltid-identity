@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.environment.api.wallet

import id.walt.commons.testing.E2ETest
import id.walt.test.integration.expectSuccess
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SilentExchangeApi(private val e2e: E2ETest, private val client: HttpClient) {

    suspend fun silentClaimRaw(walletId: Uuid, did: String, offerUrl: String) =
        client.post("/wallet-api/wallet/$walletId/api/useOfferRequest/$did") {
            contentType(ContentType.Text.Plain)
            setBody(offerUrl)
        }

    suspend fun silentClaim(walletId: Uuid, did: String, offerUrl: String): Int =
        silentClaimRaw(walletId, did, offerUrl).let {
            it.expectSuccess()
            it.body<Int>()
        }
}
