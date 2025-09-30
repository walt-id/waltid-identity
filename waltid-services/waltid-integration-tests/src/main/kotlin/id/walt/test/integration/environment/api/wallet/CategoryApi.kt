@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.environment.api.wallet

import id.walt.commons.testing.E2ETest
import id.walt.test.integration.expectSuccess
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.json.JsonObject
import java.net.URLEncoder
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class CategoryApi(private val e2e: E2ETest, private val client: HttpClient) {

    suspend fun createCategoryRaw(walletId: Uuid, categoryName: String) =
        client.post("/wallet-api/wallet/$walletId/categories/${URLEncoder.encode(categoryName, "utf-8")}/add")

    suspend fun createCategory(walletId: Uuid, categoryName: String) {
        createCategoryRaw(walletId, categoryName).expectSuccess()
    }

    suspend fun deleteCategoryRaw(walletId: Uuid, categoryName: String) =
        client.delete("/wallet-api/wallet/$walletId/categories/${URLEncoder.encode(categoryName, "utf-8")}")

    suspend fun deleteCategory(walletId: Uuid, categoryName: String) {
        deleteCategoryRaw(walletId, categoryName).expectSuccess()
    }

    suspend fun renameCategoryRaw(walletId: Uuid, from: String, to: String) =
        client.put(
            "/wallet-api/wallet/$walletId/categories/${URLEncoder.encode(from, "utf-8")}/rename/${
                URLEncoder.encode(
                    to, "utf-8"
                )
            }"
        )

    suspend fun renameCategory(walletId: Uuid, from: String, to: String) {
        renameCategoryRaw(walletId, from, to).expectSuccess()
    }

    suspend fun listCategoriesRaw(walletId: Uuid) =
        client.get("/wallet-api/wallet/$walletId/categories")

    suspend fun listCategories(walletId: Uuid): List<JsonObject> {
        return listCategoriesRaw(walletId)
            .expectSuccess()
            .body<List<JsonObject>>()
    }

    suspend fun delete(wallet: Uuid, name: String) =
        e2e.test("/wallet-api/wallet/{wallet}/categories/{name} - delete category") {
            client.delete("/wallet-api/wallet/$wallet/categories/${URLEncoder.encode(name, "utf-8")}").expectSuccess()
        }
}
