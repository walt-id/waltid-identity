package id.walt.webwallet.service.category

import id.walt.webwallet.db.models.Category
import id.walt.webwallet.db.models.CategoryData
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

object CategoryServiceImpl : CategoryService {

    override fun list(): List<CategoryData> = transaction {
        Category.selectAll().map {
            CategoryData(it)
        }
    }

    override fun add(name: String) = transaction {
        Category.insert {
            it[Category.name] = name
        }.insertedCount
    }

    override fun delete(name: String) = transaction {
        Category.deleteWhere { Category.name eq name }
    }
}