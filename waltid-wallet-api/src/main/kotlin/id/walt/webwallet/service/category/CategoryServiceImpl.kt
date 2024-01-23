package id.walt.webwallet.service.category

import id.walt.webwallet.db.models.WalletCategory
import id.walt.webwallet.db.models.WalletCategoryData
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object CategoryServiceImpl : CategoryService {

    override fun list(): List<WalletCategoryData> = transaction {
        WalletCategory.selectAll().map {
            WalletCategoryData(it)
        }
    }

    override fun add(name: String) = transaction {
        WalletCategory.insert {
            it[WalletCategory.name] = name
        }.insertedCount
    }

    override fun delete(name: String) = transaction {
        WalletCategory.deleteWhere { WalletCategory.name eq name }
    }
}