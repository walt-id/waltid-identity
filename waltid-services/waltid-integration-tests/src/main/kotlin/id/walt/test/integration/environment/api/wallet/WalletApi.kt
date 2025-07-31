@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.environment.api.wallet

import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.webwallet.db.models.Account
import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.service.credentials.CredentialFilterObject
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import id.walt.webwallet.web.model.X5CAccountRequest
import io.ktor.client.*
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class WalletApi(
    val defaultEmailAccount: EmailAccountRequest,
    val clientFactory: (String?) -> HttpClient,
    val e2e: E2ETest,
    token: String? = null
) {
    val httpClient = clientFactory(token)
    val authApi = AuthApi(e2e, httpClient)
    val keysApi = KeysApi(e2e, httpClient)
    val didsApi = DidsApi(e2e, httpClient)
    val categoryApi = CategoryApi(e2e, httpClient)
    val exchangeApi = ExchangeApi(e2e, httpClient)
    val credentialApi = CredentialsApi(e2e, httpClient)

    fun withToken(token: String): WalletApi = WalletApi(defaultEmailAccount, clientFactory, e2e, token)

    suspend fun registerX5cUserRaw(request: X5CAccountRequest) = authApi.registerX5cUserRaw(request)
    suspend fun loginX5cUserRaw(request: X5CAccountRequest) = authApi.loginX5cUserRaw(request)

    suspend fun loginWithDefaultUserRaw() = authApi.loginEmailAccountUserRaw(defaultEmailAccount)

    suspend fun loginWithDefaultUser() = login(defaultEmailAccount)

    suspend fun login(request: AccountRequest): WalletApi {
        return when (request) {
            is X5CAccountRequest -> authApi.loginX5cUser(request)
            is EmailAccountRequest -> authApi.loginEmailAccountUser(request)
            else -> throw IllegalArgumentException("Unsupported request type: ${request::class.simpleName}")
        }.let { userData ->
            val token = userData["token"]!!.jsonPrimitive.content
            WalletApi(defaultEmailAccount, clientFactory, e2e, token)
        }
    }

    suspend fun userInfoRaw() = authApi.userInfoRaw()

    suspend fun userInfo(): Account = authApi.userInfo()

    suspend fun userSessionRaw() = authApi.userSessionRaw()

    suspend fun userSession() = authApi.userSession()

    suspend fun listAccountWalletsRaw() = authApi.listAccountWalletsRaw()

    suspend fun listAccountWallets() = authApi.listAccountWallets()

    //=========================================================================
    // Keys API
    //=========================================================================
    suspend fun listKeys(walletId: Uuid) = keysApi.list(walletId)
    suspend fun generateKey(walletId: Uuid, request: KeyGenerationRequest) = keysApi.generate(walletId, request)
    suspend fun loadKey(walletId: Uuid, keyId: String) = keysApi.load(walletId, keyId)
    suspend fun loadKeyMeta(walletId: Uuid, keyId: String) = keysApi.loadMeta(walletId, keyId)
    suspend fun exportKey(walletId: Uuid, keyId: String, format: String, isPrivate: Boolean) =
        keysApi.exportKey(walletId, keyId, format, isPrivate)

    suspend fun deleteKeyRaw(walletId: Uuid, keyId: String) = keysApi.deleteKeyRaw(walletId, keyId)
    suspend fun deleteKey(walletId: Uuid, keyId: String) = keysApi.deleteKey(walletId, keyId)
    suspend fun importKey(walletId: Uuid, keyId: String): String = keysApi.importKey(walletId, keyId)

    //=========================================================================
    // Dids API
    //=========================================================================
    suspend fun listDids(walletId: Uuid) = didsApi.listDids(walletId)
    suspend fun setDefaultDid(walletId: Uuid, did: String) = didsApi.setDefaultDid(walletId, did)
    suspend fun createDidRaw(
        walletId: Uuid,
        method: String,
        keyId: String? = null,
        alias: String? = null,
        options: Map<String, Any> = emptyMap()
    ) = didsApi.createDidRaw(walletId, method, keyId, alias, options)

    suspend fun createDid(
        walletId: Uuid,
        method: String,
        keyId: String? = null,
        alias: String? = null,
        options: Map<String, Any> = emptyMap()
    ) = didsApi.createDid(walletId, method, keyId, alias, options)

    suspend fun getDid(walletId: Uuid, did: String) = didsApi.getDid(walletId, did)
    suspend fun getDefaultDid(walletId: Uuid): WalletDid {
        val possibleDefaultDids = listDids(walletId).filter { it.default }
        assertEquals(1, possibleDefaultDids.size, "Expected wallet '${walletId}' to have exact one default did.")
        return possibleDefaultDids.get(0)
    }

    suspend fun deleteDidRaw(walletId: Uuid, didString: String) =
        didsApi.deleteDidRaw(walletId, didString)

    suspend fun deleteDid(walletId: Uuid, didString: String) =
        didsApi.deleteDid(walletId, didString)

    //=========================================================================
    // Category API
    //=========================================================================
    suspend fun createCategory(walletId: Uuid, categoryName: String) =
        categoryApi.createCategory(walletId, categoryName)

    suspend fun deleteCategory(walletId: Uuid, categoryName: String) =
        categoryApi.deleteCategory(walletId, categoryName)

    suspend fun renameCategory(walletId: Uuid, from: String, to: String) =
        categoryApi.renameCategory(walletId, from, to)

    suspend fun listCategories(walletId: Uuid) =
        categoryApi.listCategories(walletId)

    //=========================================================================
    // Credential Exchange
    //=========================================================================
    suspend fun resolveCredentialOffer(walletId: Uuid, offerUrl: String) =
        exchangeApi.resolveCredentialOffer(walletId, offerUrl)

    suspend fun claimCredential(walletId: Uuid, offerUrl: String) =
        exchangeApi.claimCredential(walletId, offerUrl)

    //=========================================================================
    // Credential Store
    //=========================================================================
    suspend fun getCredential(walletId: Uuid, credentialId: String) =
        credentialApi.getCredential(walletId, credentialId)

    suspend fun getCredentialStatus(walletId: Uuid, credentialId: String) =
        credentialApi.getCredentialStatus(walletId, credentialId)


    suspend fun listCredentials(walletId: Uuid, filter: CredentialFilterObject = CredentialFilterObject.default) =
        credentialApi.listCredentials(walletId, filter)

    suspend fun acceptCredential(walletId: Uuid, credentialId: String) =
        credentialApi.acceptCredential(walletId, credentialId)

    suspend fun deleteCredential(walletId: Uuid, credentialId: String, permanent: Boolean = false) =
        credentialApi.deleteCredential(walletId, credentialId, permanent)

    suspend fun restoreCredential(walletId: Uuid, credentialId: String) =
        credentialApi.restoreCredential(walletId, credentialId)


}