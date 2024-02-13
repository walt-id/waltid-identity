package id.walt.webwallet.service.settings

import id.walt.webwallet.db.models.WalletSettings
import kotlinx.serialization.Serializable
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

object SettingsService {

    fun get(wallet: UUID) = transaction {
        getQuery(wallet).singleOrNull()?.let {
            WalletSetting(showNoteOnPresentation = it[WalletSettings.showNoteOnPresentation])
        }
    }

    fun set(wallet: UUID, setting: WalletSetting) = transaction {
        upsertQuery(wallet, setting)
    }.insertedCount

    private fun getQuery(wallet: UUID) = WalletSettings.select { WalletSettings.wallet eq wallet }

    private fun upsertQuery(wallet: UUID, setting: WalletSetting) = WalletSettings.upsert(
        WalletSettings.wallet
    ) {
        it[WalletSettings.wallet] = wallet
        it[WalletSettings.showNoteOnPresentation] = setting.showNoteOnPresentation
    }
}

@Serializable
data class WalletSetting(
    val showNoteOnPresentation: Boolean
) {
    companion object {
        val default = WalletSetting(false)
    }
}