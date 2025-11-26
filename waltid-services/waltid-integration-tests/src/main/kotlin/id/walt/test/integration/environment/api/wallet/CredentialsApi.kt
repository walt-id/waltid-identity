@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.environment.api.wallet

import id.walt.commons.testing.E2ETest
import id.walt.test.integration.expectSuccess
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.credentials.CredentialFilterObject
import id.walt.webwallet.usecase.credential.CredentialStatusResult
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.json.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class CredentialsApi(private val e2e: E2ETest, private val client: HttpClient) {

    suspend fun getCredentialRaw(walletId: Uuid, credentialId: String) =
        client.get("/wallet-api/wallet/$walletId/credentials/$credentialId")

    suspend fun getCredential(walletId: Uuid, credentialId: String) =
        getCredentialRaw(walletId, credentialId).let {
            it.expectSuccess()
            it.body<WalletCredential>()
        }

    suspend fun getCredentialStatusRaw(walletId: Uuid, credentialId: String) =
        client.get("/wallet-api/wallet/$walletId/credentials/$credentialId/status")

    suspend fun getCredentialStatus(walletId: Uuid, credentialId: String): List<CredentialStatusResult> =
        getCredentialStatusRaw(walletId, credentialId).let {
            it.expectSuccess()
            it.body<List<CredentialStatusResult>>()
        }

    suspend fun listCredentialsRaw(
        wallet: Uuid,
        filter: CredentialFilterObject = CredentialFilterObject.default
    ) = client.get("/wallet-api/wallet/$wallet/credentials") {
        url {
            filter.toMap().onEach {
                if (it.key == "categories" && it.value is JsonArray) {
                    it.value.jsonArray.forEach { category ->
                        parameters.append("category", category.jsonPrimitive.content)
                    }
                } else {
                    if (it.value !is JsonNull && !it.value.jsonPrimitive.content.isBlank()) {
                        parameters.append(it.key, it.value.toString())
                    }
                }
            }
        }
    }

    suspend fun listCredentials(
        wallet: Uuid,
        filter: CredentialFilterObject = CredentialFilterObject.default
    ) = listCredentialsRaw(wallet, filter).let { response ->
        response.expectSuccess()
        response.body<List<WalletCredential>>()
    }

    suspend fun acceptCredentialRaw(walletId: Uuid, credentialId: String) =
        client.post("/wallet-api/wallet/$walletId/credentials/$credentialId/accept")

    suspend fun acceptCredential(walletId: Uuid, credentialId: String) {
        acceptCredentialRaw(walletId, credentialId).expectSuccess()
    }

    suspend fun deleteCredentialRaw(walletId: Uuid, credentialId: String, permanent: Boolean = false) =
        client.delete("/wallet-api/wallet/$walletId/credentials/$credentialId") {
            url {
                parameters.append("permanent", "$permanent")
            }
        }

    suspend fun deleteCredential(walletId: Uuid, credentialId: String, permanent: Boolean = false) {
        deleteCredentialRaw(walletId, credentialId, permanent).expectSuccess()
    }

    suspend fun restoreCredentialRaw(walletId: Uuid, credentialId: String) =
        client.post("/wallet-api/wallet/$walletId/credentials/$credentialId/restore")

    suspend fun restoreCredential(walletId: Uuid, credentialId: String) {
        restoreCredentialRaw(walletId, credentialId).expectSuccess()
    }

    suspend fun attachCategoriesToCredentialRaw(walletId: Uuid, credentialId: String, vararg categories: String) =
        client.put("/wallet-api/wallet/$walletId/credentials/$credentialId/category") {
            setBody(categories.toList())
        }

    suspend fun attachCategoriesToCredential(walletId: Uuid, credentialId: String, vararg categories: String) {
        attachCategoriesToCredentialRaw(walletId, credentialId, *categories).expectSuccess()
    }

    suspend fun detachCategoriesFromCredentialRaw(wallet: Uuid, credential: String, vararg categories: String) =
        client.delete("/wallet-api/wallet/$wallet/credentials/$credential/category") {
            setBody(categories.toList())
        }

    suspend fun detachCategoriesFromCredential(wallet: Uuid, credential: String, vararg categories: String) {
        detachCategoriesFromCredentialRaw(wallet, credential, *categories).expectSuccess()
    }

    @Deprecated("Old API")
    suspend fun delete(wallet: Uuid, credential: String, permanent: Boolean = false) =
        e2e.test("/wallet-api/wallet/{wallet}/credentials/{credentialId} - delete credential") {
            client.delete("/wallet-api/wallet/$wallet/credentials/$credential") {
                url {
                    parameters.append("permanent", "$permanent")
                }
            }.expectSuccess()
        }

    suspend fun restore(wallet: Uuid, credential: String, output: ((WalletCredential) -> Unit)? = null) =
        e2e.test("/wallet-api/wallet/{wallet}/credentials/{credentialId}/restore - restore credential") {
            client.post("/wallet-api/wallet/$wallet/credentials/$credential/restore").expectSuccess().apply {
                output?.invoke(body<WalletCredential>())
            }
        }


    suspend fun reject(wallet: Uuid, credential: String, note: String? = null) =
        e2e.test("/wallet-api/wallet/{wallet}/credentials/{credentialId}/reject - reject credential") {
            client.post("/wallet-api/wallet/$wallet/credentials/$credential/reject") {
                setBody(mapOf("note" to note))
            }.expectSuccess()
        }

    suspend fun status(wallet: Uuid, credential: String, output: ((List<CredentialStatusResult>) -> Unit)? = null) =
        e2e.test("/wallet-api/wallet/{wallet}/credentials/{credentialId}/status - get credential status") {
            client.get("/wallet-api/wallet/$wallet/credentials/$credential/status").expectSuccess().apply {
                val result = body<List<CredentialStatusResult>>()
                output?.invoke(result)
            }
        }


    suspend fun store(wallet: Uuid, credential: String) =
        e2e.test("/wallet-api/wallet/{wallet}/credentials - store credential") {
            TODO("Not implemented")
        }

    private fun CredentialFilterObject.toMap() = Json.encodeToJsonElement(this).jsonObject.toMap()
}
