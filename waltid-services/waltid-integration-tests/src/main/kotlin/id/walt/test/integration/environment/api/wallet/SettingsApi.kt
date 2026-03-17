@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.environment.api.wallet

import id.walt.commons.testing.E2ETest
import id.walt.test.integration.expectSuccess
import id.walt.webwallet.service.settings.WalletSetting
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SettingsApi(private val e2e: E2ETest, private val client: HttpClient) {

    suspend fun getSettingsRaw(walletId: Uuid) =
        client.get("/wallet-api/wallet/$walletId/settings")

    suspend fun getSettings(walletId: Uuid): WalletSetting =
        getSettingsRaw(walletId).let {
            it.expectSuccess()
            it.body<WalletSetting>()
        }

    suspend fun updateSettingsRaw(walletId: Uuid, settings: JsonObject) =
        client.post("/wallet-api/wallet/$walletId/settings") {
            contentType(ContentType.Application.Json)
            setBody(settings)
        }

    suspend fun updateSettings(walletId: Uuid, settings: JsonObject) {
        updateSettingsRaw(walletId, settings).expectSuccess()
    }
}
