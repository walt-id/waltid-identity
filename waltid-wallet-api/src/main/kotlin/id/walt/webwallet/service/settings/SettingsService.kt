package id.walt.webwallet.service.settings

import id.walt.webwallet.db.models.WalletSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

object SettingsService {
    private val json = Json { ignoreUnknownKeys = true }

    fun get(wallet: UUID) = transaction {
        tryParseSettings(getQuery(wallet).singleOrNull()?.get(WalletSettings.settings))
    }

    fun set(wallet: UUID, setting: JsonObject) = transaction {
        upsertQuery(wallet, setting)
    }.insertedCount

    private fun getQuery(wallet: UUID) = WalletSettings.selectAll().where { WalletSettings.wallet eq wallet }

    private fun upsertQuery(wallet: UUID, setting: JsonObject) = WalletSettings.upsert(
        WalletSettings.wallet
    ) {
        it[WalletSettings.wallet] = wallet
        it[WalletSettings.settings] = json.encodeToString(setting)
    }

    private fun tryParseSettings(settings: String?) =
        runCatching { json.decodeFromString<JsonObject>(settings ?: "") }.fold(onSuccess = { WalletSetting(it) },
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
