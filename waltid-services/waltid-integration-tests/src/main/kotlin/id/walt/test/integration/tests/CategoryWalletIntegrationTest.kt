@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import id.walt.test.integration.environment.api.wallet.WalletApi
import id.walt.webwallet.db.models.AccountWalletListing.WalletListing
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

class CategoryWalletIntegrationTest : AbstractIntegrationTest() {

    companion object {

        lateinit var walletApi: WalletApi
        lateinit var wallet: WalletListing

        @JvmStatic
        @BeforeAll
        fun loadWalletAndDefaultDid(): Unit = runBlocking {
            walletApi = getDefaultAccountWalletApi()
            wallet = walletApi.listAccountWallets().wallets.first()
        }

        @JvmStatic
        @BeforeAll
        @AfterAll
        fun deleteAllCategories() = runBlocking {
            walletApi.listCategories(wallet.id).forEach {
                walletApi.deleteCategory(wallet.id, it["name"]!!.jsonPrimitive.content)
            }
        }
    }

    @Test
    fun shouldCreateCategory() = runTest {
        walletApi.createCategory(wallet.id, "My-New-Category")
        assertTrue(walletApi.listCategories(wallet.id).any { category ->
            "My-New-Category" == category["name"]?.jsonPrimitive?.content
        }, "Category was not created")
    }

    @Disabled("Spaces are replace with '+' character")
    @Test
    fun shouldCreateCategoryWithSpaceInTheName() = runTest {
        walletApi.createCategory(wallet.id, "My New Category")
        assertTrue(walletApi.listCategories(wallet.id).any { category ->
            "My New Category" == category["name"]?.jsonPrimitive?.content
        }, "Category was not created")
    }

    @Test
    fun shouldRenameCategory() = runTest {
        val originalName = "Category-to-rename"
        val newName = "Category-with-new-name"
        walletApi.createCategory(wallet.id, originalName)
        walletApi.renameCategory(wallet.id, originalName, newName)
        assertTrue(walletApi.listCategories(wallet.id).any {
            newName == it["name"]?.jsonPrimitive?.content
        }, "Category was not renamed")
    }

    @Test
    fun shouldDeleteCategory() = runTest {
        val categoryName = "Category-to-delete"
        walletApi.createCategory(wallet.id, categoryName)
        assertTrue(walletApi.listCategories(wallet.id).any {
            categoryName == it["name"]?.jsonPrimitive?.content
        }, "Category was not created")
        walletApi.deleteCategory(wallet.id, categoryName)
        assertFalse(walletApi.listCategories(wallet.id).any {
            categoryName == it["name"]?.jsonPrimitive?.content
        }, "Category was not deleted")
    }
}