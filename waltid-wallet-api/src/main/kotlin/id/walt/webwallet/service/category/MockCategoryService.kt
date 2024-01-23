package id.walt.webwallet.service.category

import id.walt.webwallet.db.models.CategoryData
import kotlin.random.Random

object MockCategoryService : CategoryService {
    override fun list(): List<CategoryData> = listOfCategories

    override fun add(name: String) = 1

    override fun delete(name: String) = 1

    private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    private val listOfCategories = (1..10).map { CategoryData(name = generate(Random.nextInt(4, 17))) }
    private fun generate(length: Int) =
        (1..length).map { Random.nextInt(0, charPool.size).let { charPool[it] } }.joinToString("")

}