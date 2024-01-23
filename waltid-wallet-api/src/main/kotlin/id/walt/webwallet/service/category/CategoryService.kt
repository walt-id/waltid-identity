package id.walt.webwallet.service.category

import id.walt.webwallet.db.models.CategoryData

interface CategoryService {
    fun list(): List<CategoryData>
    fun add(name: String): Int
    fun delete(name: String): Int
}