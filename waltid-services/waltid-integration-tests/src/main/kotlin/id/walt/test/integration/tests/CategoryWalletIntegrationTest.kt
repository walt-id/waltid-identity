@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi

class CategoryWalletIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun shouldCreateCategory() = runTest {
        defaultWalletApi.createCategory("My-New-Category")
        assertTrue(defaultWalletApi.listCategories().any { category ->
            "My-New-Category" == category["name"]?.jsonPrimitive?.content
        }, "Category was not created")
    }

    @Disabled("Spaces are replace with '+' character")
    @Test
    fun shouldCreateCategoryWithSpaceInTheName() = runTest {
        defaultWalletApi.createCategory("My New Category")
        assertTrue(defaultWalletApi.listCategories().any { category ->
            "My New Category" == category["name"]?.jsonPrimitive?.content
        }, "Category was not created")
    }

    @Test
    fun shouldRenameCategory() = runTest {
        val originalName = "Category-to-rename"
        val newName = "Category-with-new-name"
        defaultWalletApi.createCategory(originalName)
        defaultWalletApi.renameCategory(originalName, newName)
        assertTrue(defaultWalletApi.listCategories().any {
            newName == it["name"]?.jsonPrimitive?.content
        }, "Category was not renamed")
    }

    @Test
    fun shouldDeleteCategory() = runTest {
        val categoryName = "Category-to-delete"
        defaultWalletApi.createCategory(categoryName)
        assertTrue(defaultWalletApi.listCategories().any {
            categoryName == it["name"]?.jsonPrimitive?.content
        }, "Category was not created")
        defaultWalletApi.deleteCategory(categoryName)
        assertFalse(defaultWalletApi.listCategories().any {
            categoryName == it["name"]?.jsonPrimitive?.content
        }, "Category was not deleted")
    }
}