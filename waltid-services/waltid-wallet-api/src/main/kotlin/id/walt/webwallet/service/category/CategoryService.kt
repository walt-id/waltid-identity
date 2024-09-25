package id.walt.webwallet.service.category

import id.walt.webwallet.db.models.WalletCategoryData
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


@OptIn(ExperimentalUuidApi::class)
interface CategoryService {
    fun list(wallet: Uuid): List<WalletCategoryData>
    fun get(wallet: Uuid, name: String): WalletCategoryData?
    fun add(wallet: Uuid, name: String): Int
    fun delete(wallet: Uuid, name: String): Int
    fun rename(wallet: Uuid, oldName: String, newName: String): Int
}
