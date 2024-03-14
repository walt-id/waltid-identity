package id.walt.webwallet.service.category

import id.walt.webwallet.db.models.WalletCategoryData
import kotlinx.uuid.UUID

interface CategoryService {
    fun list(wallet: UUID): List<WalletCategoryData>
    fun get(wallet: UUID, name: String): WalletCategoryData?
    fun add(wallet: UUID, name: String): Int
    fun delete(wallet: UUID, name: String): Int
    fun rename(wallet: UUID, oldName: String, newName: String): Int
}