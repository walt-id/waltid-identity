package id.walt.webwallet.service.category

import id.walt.webwallet.db.models.WalletCategory
import id.walt.webwallet.db.models.WalletCategoryData
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

object CategoryServiceImpl : CategoryService {

    override fun list(wallet: UUID): List<WalletCategoryData> = transaction {
        WalletCategory.selectAll().where { WalletCategory.wallet eq wallet }.map {
            WalletCategoryData(it)
        }
    }

    override fun get(wallet: UUID, name: String): WalletCategoryData? = transaction {
        WalletCategory.selectAll().where { WalletCategory.wallet eq wallet and (WalletCategory.name eq name) }.singleOrNull()
            ?.let { WalletCategoryData(it) }
    }

    override fun add(wallet: UUID, name: String) = transaction {
        WalletCategory.insert {
            it[WalletCategory.wallet] = wallet
            it[WalletCategory.name] = name
        }.insertedCount
    }

    override fun delete(wallet: UUID, name: String) = transaction {
        WalletCategory.deleteWhere { WalletCategory.wallet eq wallet and (WalletCategory.name eq name) }
    }

    override fun rename(wallet: UUID, oldName: String, newName: String): Int = transaction {
        WalletCategory.update({ WalletCategory.wallet eq wallet and (WalletCategory.name eq oldName) }) {
            it[this.name] = newName
        }
    }
}
