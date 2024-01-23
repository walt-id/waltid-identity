package id.walt.webwallet.service.category

import id.walt.webwallet.db.models.WalletCategory
import id.walt.webwallet.db.models.WalletCategoryData
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object CategoryServiceImpl : CategoryService {

    override fun list(wallet: UUID): List<WalletCategoryData> = transaction {
        WalletCategory.select { WalletCategory.wallet eq wallet }.map {
            WalletCategoryData(it)
        }
    }

    override fun get(wallet: UUID, name: String): WalletCategoryData? = list(wallet).firstOrNull { it.name == name }

    override fun add(wallet: UUID, name: String) = transaction {
        WalletCategory.insert {
            it[WalletCategory.wallet] = wallet
            it[WalletCategory.name] = name
        }.insertedCount
    }

    override fun delete(wallet: UUID, name: String) = transaction {
        WalletCategory.deleteWhere { WalletCategory.wallet eq wallet and (WalletCategory.name eq name) }
    }
}