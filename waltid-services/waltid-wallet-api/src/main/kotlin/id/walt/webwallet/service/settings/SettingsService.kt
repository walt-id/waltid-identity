@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.service.settings

import id.walt.webwallet.db.models.WalletSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

object SettingsService {
    private val json = Json { ignoreUnknownKeys = true }

    fun get(wallet: Uuid) = transaction {
        tryParseSettings(getQuery(wallet).singleOrNull()?.get(WalletSettings.settings))
    }

    fun set(wallet: Uuid, setting: JsonObject) = transaction {
        upsertQuery(wallet, setting)
    }.insertedCount

    private fun getQuery(wallet: Uuid) = WalletSettings.selectAll().where { WalletSettings.wallet eq wallet.toJavaUuid() }

    private fun upsertQuery(wallet: Uuid, setting: JsonObject) = WalletSettings.upsert(
        WalletSettings.wallet
    ) {
        it[WalletSettings.wallet] = wallet.toJavaUuid()
        it[settings] = json.encodeToString(setting)
    }

    private fun tryParseSettings(settings: String?) =
        runCatching { json.decodeFromString<JsonObject>(settings ?: "") }.fold(
            onSuccess = { WalletSetting(it) },
            onFailure = { WalletSetting.default })
}

@Serializable
data class WalletSetting(
    val settings: JsonObject,
) {
    companion object {
        val default = WalletSetting(JsonObject(emptyMap()))
    }
}
