package id.walt.webwallet.service.category

import id.walt.webwallet.db.models.WalletCategoryData
import kotlinx.uuid.UUID
import kotlin.random.Random

object MockCategoryService : CategoryService {
    override fun list(wallet: UUID): List<WalletCategoryData> = listOfCategories
    override fun get(wallet: UUID, name: String): WalletCategoryData =
        listOfCategories[Random.nextInt(listOfCategories.size)]

    override fun add(wallet: UUID, name: String) = 1

    override fun delete(wallet: UUID, name: String) = 1

    private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    private val listOfCategories = (1..10).map { WalletCategoryData(name = generate(Random.nextInt(4, 17))) }
    private fun generate(length: Int) =
        (1..length).map { Random.nextInt(0, charPool.size).let { charPool[it] } }.joinToString("")

}