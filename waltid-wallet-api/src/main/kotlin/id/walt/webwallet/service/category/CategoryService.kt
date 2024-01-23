package id.walt.webwallet.service.category

import id.walt.webwallet.db.models.WalletCategoryData

interface CategoryService {
    fun list(): List<WalletCategoryData>
    fun add(name: String): Int
    fun delete(name: String): Int
}