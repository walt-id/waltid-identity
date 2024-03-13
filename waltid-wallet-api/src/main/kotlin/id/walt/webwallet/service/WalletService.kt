package id.walt.webwallet.service

import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.webwallet.db.models.WalletCategoryData
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.db.models.WalletOperationHistory
import id.walt.webwallet.service.credentials.CredentialFilterObject
import id.walt.webwallet.service.dto.LinkedWalletDataTransferObject
import id.walt.webwallet.service.dto.WalletDataTransferObject
import id.walt.webwallet.service.events.EventLogFilter
import id.walt.webwallet.service.events.EventLogFilterResult
import id.walt.webwallet.service.keys.SingleKeyResponse
import id.walt.webwallet.service.report.ReportRequestParameter
import id.walt.webwallet.service.settings.WalletSetting
import id.walt.webwallet.web.controllers.PresentationRequestParameter
import id.walt.webwallet.web.parameter.CredentialRequestParameter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.uuid.UUID

abstract class WalletService(val tenant: String, val accountId: UUID, val walletId: UUID) {

    // WalletCredentials
    abstract fun listCredentials(filter: CredentialFilterObject): List<WalletCredential>
    abstract suspend fun listRawCredentials(): List<String>
    abstract suspend fun deleteCredential(id: String, permanent: Boolean): Boolean
    abstract suspend fun restoreCredential(id: String): WalletCredential
    abstract suspend fun getCredential(credentialId: String): WalletCredential
    abstract suspend fun acceptCredential(parameter: CredentialRequestParameter): Boolean
    abstract suspend fun rejectCredential(parameter: CredentialRequestParameter): Boolean
    abstract suspend fun attachCategory(credentialId: String, categories: List<String>): Boolean
    abstract suspend fun detachCategory(credentialId: String, categories: List<String>): Boolean
    abstract suspend fun renameCategory(oldName: String, newName: String): Boolean
    abstract fun getCredentialsByIds(credentialIds: List<String>): List<WalletCredential>

    abstract fun matchCredentialsByPresentationDefinition(presentationDefinition: PresentationDefinition): List<WalletCredential>

    // SIOP
    abstract suspend fun usePresentationRequest(parameter: PresentationRequestParameter): Result<String?>

    abstract suspend fun resolvePresentationRequest(request: String): String
    abstract suspend fun useOfferRequest(
        offer: String, did: String, requireUserInput: Boolean
    ): List<WalletCredential>
    abstract suspend fun resolveCredentialOffer(offerRequest: CredentialOfferRequest): CredentialOffer

    // DIDs
    abstract suspend fun listDids(): List<WalletDid>
    abstract suspend fun loadDid(did: String): JsonObject
    abstract suspend fun createDid(method: String, args: Map<String, JsonPrimitive> = emptyMap()): String
    abstract suspend fun deleteDid(did: String): Boolean
    abstract suspend fun setDefault(did: String)

    // Keys
    abstract suspend fun listKeys(): List<SingleKeyResponse>
    abstract suspend fun generateKey(type: String): String
    abstract suspend fun exportKey(alias: String, format: String, private: Boolean): String
    abstract suspend fun loadKey(alias: String): JsonObject
    abstract suspend fun importKey(jwkOrPem: String): String
    abstract suspend fun deleteKey(alias: String): Boolean

    // History
    abstract fun getHistory(limit: Int = 10, offset: Long = 0): List<WalletOperationHistory>
    abstract suspend fun addOperationHistory(operationHistory: WalletOperationHistory)

    // EventLog
    abstract fun filterEventLog(filter: EventLogFilter): EventLogFilterResult

    // Web3 wallets
    abstract suspend fun linkWallet(wallet: WalletDataTransferObject): LinkedWalletDataTransferObject
    abstract suspend fun unlinkWallet(wallet: UUID): Boolean
    abstract suspend fun getLinkedWallets(): List<LinkedWalletDataTransferObject>
    abstract suspend fun connectWallet(walletId: UUID): Boolean
    abstract suspend fun disconnectWallet(wallet: UUID): Boolean

    // TODO: move each such component to use-case

    // Categories
    abstract suspend fun listCategories(): List<WalletCategoryData>
    abstract suspend fun addCategory(name: String): Boolean
    abstract suspend fun deleteCategory(name: String): Boolean

    // Reports
    abstract suspend fun getFrequentCredentials(parameter: ReportRequestParameter): List<WalletCredential>

    // Settings
    abstract suspend fun getSettings(): WalletSetting
    abstract suspend fun setSettings(settings: JsonObject): Boolean


    // TODO: Push

}
