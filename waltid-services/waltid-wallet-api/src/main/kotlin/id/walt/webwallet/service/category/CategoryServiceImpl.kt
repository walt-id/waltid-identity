package id.walt.webwallet.service.category

import id.walt.webwallet.db.models.WalletCategory
import id.walt.webwallet.db.models.WalletCategoryData

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@ExperimentalUuidApi
object CategoryServiceImpl : CategoryService {

    override fun list(wallet: Uuid): List<WalletCategoryData> = transaction {
        WalletCategory.selectAll().where { WalletCategory.wallet eq wallet.toJavaUuid() }.map {
            WalletCategoryData(it)
        }
    }

    override fun get(wallet: Uuid, name: String): WalletCategoryData? = transaction {
        WalletCategory.selectAll().where { WalletCategory.wallet eq wallet.toJavaUuid() and (WalletCategory.name eq name) }.singleOrNull()
            ?.let { WalletCategoryData(it) }
    }

    override fun add(wallet: Uuid, name: String) = transaction {
        WalletCategory.insert {
            it[WalletCategory.wallet] = wallet.toJavaUuid()
            it[WalletCategory.name] = name
        }.insertedCount
    }

    override fun delete(wallet: Uuid, name: String) = transaction {
        WalletCategory.deleteWhere { WalletCategory.wallet eq wallet.toJavaUuid() and (WalletCategory.name eq name) }
    }

    override fun rename(wallet: Uuid, oldName: String, newName: String): Int = transaction {
        WalletCategory.update({ WalletCategory.wallet eq wallet.toJavaUuid() and (WalletCategory.name eq oldName) }) {
            it[this.name] = newName
        }
    }
}
