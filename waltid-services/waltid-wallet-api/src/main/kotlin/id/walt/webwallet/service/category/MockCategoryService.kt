package id.walt.webwallet.service.category

import id.walt.webwallet.db.models.WalletCategoryData

import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
object MockCategoryService : CategoryService {
    override fun list(wallet: Uuid): List<WalletCategoryData> = listOfCategories
    override fun get(wallet: Uuid, name: String): WalletCategoryData =
        listOfCategories[Random.nextInt(listOfCategories.size)]

    override fun add(wallet: Uuid, name: String) = 1

    override fun delete(wallet: Uuid, name: String) = 1
    override fun rename(wallet: Uuid, oldName: String, newName: String): Int = 1

    private val charPool: CharArray = (('a'..'z') + ('A'..'Z') + ('0'..'9')).toCharArray()
    private val listOfCategories = (1..10).map { WalletCategoryData(name = generate(Random.nextInt(4, 17))) }
    private fun generate(length: Int) = buildString(length) {
        repeat(length) { append(charPool.random()) }
    }

}
