@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.environment.api.wallet

import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.webwallet.db.models.AccountWalletListing.WalletListing
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.service.credentials.CredentialFilterObject
import id.walt.webwallet.service.keys.SingleKeyResponse
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import io.ktor.client.*
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class WalletApi(
    val walletId: Uuid,
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

    suspend fun getWallet(): WalletListing =
        authApi.listAccountWallets().wallets.first { it.id == walletId }

    //=========================================================================
    // Keys API
    //=========================================================================
    suspend fun listKeys(): List<SingleKeyResponse> = keysApi.list(walletId)
    suspend fun generateKey(request: KeyGenerationRequest): String = keysApi.generate(walletId, request)
    suspend fun loadKey(keyId: String) = keysApi.load(walletId, keyId)
    suspend fun loadKeyMeta(keyId: String) = keysApi.loadMeta(walletId, keyId)
    suspend fun exportKey(keyId: String, format: String, isPrivate: Boolean) =
        keysApi.exportKey(walletId, keyId, format, isPrivate)

    suspend fun deleteKeyRaw(keyId: String) = keysApi.deleteKeyRaw(walletId, keyId)
    suspend fun deleteKey(keyId: String) = keysApi.deleteKey(walletId, keyId)
    suspend fun importKey(keyId: String): String = keysApi.importKey(walletId, keyId)

    //=========================================================================
    // Dids API
    //=========================================================================
    suspend fun listDids() = didsApi.listDids(walletId)
    suspend fun setDefaultDid(did: String) = didsApi.setDefaultDid(walletId, did)
    suspend fun createDidRaw(
        method: String,
        keyId: String? = null,
        alias: String? = null,
        options: Map<String, Any> = emptyMap()
    ) = didsApi.createDidRaw(walletId, method, keyId, alias, options)

    suspend fun createDid(
        method: String,
        keyId: String? = null,
        alias: String? = null,
        options: Map<String, Any> = emptyMap()
    ): String = didsApi.createDid(walletId, method, keyId, alias, options)

    suspend fun getDid(did: String) = didsApi.getDid(walletId, did)
    suspend fun getDefaultDid(): WalletDid {
        val possibleDefaultDids = listDids().filter { it.default }
        assertEquals(1, possibleDefaultDids.size, "Expected wallet '${walletId}' to have exact one default did.")
        return possibleDefaultDids[0]
    }

    suspend fun deleteDidRaw(didString: String) =
        didsApi.deleteDidRaw(walletId, didString)

    suspend fun deleteDid(didString: String) =
        didsApi.deleteDid(walletId, didString)

    //=========================================================================
    // Category API
    //=========================================================================
    suspend fun createCategory(categoryName: String) =
        categoryApi.createCategory(walletId, categoryName)

    suspend fun deleteCategory(categoryName: String) =
        categoryApi.deleteCategory(walletId, categoryName)

    suspend fun renameCategory(from: String, to: String) =
        categoryApi.renameCategory(walletId, from, to)

    suspend fun listCategories() =
        categoryApi.listCategories(walletId)

    //=========================================================================
    // Credential Exchange
    //=========================================================================
    suspend fun resolveCredentialOffer(offerUrl: String) =
        exchangeApi.resolveCredentialOffer(walletId, offerUrl)

    suspend fun claimCredential(
        offerUrl: String,
        didString: String? = null,
        requireUserInput: Boolean? = null,
        pinOrTxCode: String? = null
    ) =
        exchangeApi.claimCredential(walletId, offerUrl, didString, requireUserInput, pinOrTxCode)

    suspend fun resolvePresentationRequestRaw(verificationUrl: String) =
        exchangeApi.resolvePresentationRequestRaw(walletId, verificationUrl)

    suspend fun resolvePresentationRequest(verificationUrl: String) =
        exchangeApi.resolvePresentationRequest(walletId, verificationUrl)

    suspend fun matchCredentialsForPresentationDefinitionRaw(presentationDefinition: String) =
        exchangeApi.matchCredentialsForPresentationDefinitionRaw(walletId, presentationDefinition)

    suspend fun matchCredentialsForPresentationDefinition(presentationDefinition: String) =
        exchangeApi.matchCredentialsForPresentationDefinition(walletId, presentationDefinition)

    suspend fun unmatchedCredentialsForPresentationDefinition(presentationDefinition: String) =
        exchangeApi.unmatchedCredentialsForPresentationDefinition(walletId, presentationDefinition)

    suspend fun usePresentationRequest(request: UsePresentationRequest) =
        exchangeApi.usePresentationRequest(walletId, request)

    suspend fun usePresentationRequestExpectError(request: UsePresentationRequest) =
        exchangeApi.usePresentationRequestExpectError(walletId, request)


    //=========================================================================
    // Credential Store
    //=========================================================================
    suspend fun getCredential(credentialId: String) =
        credentialApi.getCredential(walletId, credentialId)

    suspend fun getCredentialStatus(credentialId: String) =
        credentialApi.getCredentialStatus(walletId, credentialId)

    suspend fun listCredentialsRaw(
        filter: CredentialFilterObject = CredentialFilterObject.default
    ) = credentialApi.listCredentialsRaw(walletId, filter)

    suspend fun listCredentials(
        filter: CredentialFilterObject = CredentialFilterObject.default
    ): List<WalletCredential> =
        credentialApi.listCredentials(walletId, filter)

    suspend fun acceptCredential(credentialId: String) =
        credentialApi.acceptCredential(walletId, credentialId)

    suspend fun deleteCredential(credentialId: String, permanent: Boolean = false) =
        credentialApi.deleteCredential(walletId, credentialId, permanent)

    suspend fun restoreCredential(credentialId: String) =
        credentialApi.restoreCredential(walletId, credentialId)

    suspend fun attachCategoriesToCredential(credentialId: String, vararg categories: String) =
        credentialApi.attachCategoriesToCredential(walletId, credentialId, *categories)

    suspend fun detachCategoriesFromCredential(credentialId: String, vararg categories: String) =
        credentialApi.detachCategoriesFromCredential(walletId, credentialId, *categories)

}