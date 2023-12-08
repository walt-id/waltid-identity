package id.walt.service

import id.walt.db.models.WalletCredential
import id.walt.db.models.WalletDid
import id.walt.db.models.WalletOperationHistory
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.service.dto.LinkedWalletDataTransferObject
import id.walt.service.dto.WalletDataTransferObject
import id.walt.service.issuers.IssuerDataTransferObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.uuid.UUID

abstract class WalletService(val accountId: UUID, val walletId: UUID) {

    // WalletCredentials
    abstract fun listCredentials(): List<WalletCredential>
    abstract suspend fun listRawCredentials(): List<String>
    abstract suspend fun deleteCredential(id: String): Boolean
    abstract suspend fun getCredential(credentialId: String): WalletCredential

    abstract fun matchCredentialsByPresentationDefinition(presentationDefinition: PresentationDefinition): List<WalletCredential>

    // SIOP
    abstract suspend fun usePresentationRequest(request: String, did: String, selectedCredentialIds: List<String>, disclosures: Map<String, List<String>>?): Result<String?>
    abstract suspend fun resolvePresentationRequest(request: String): String
    abstract suspend fun useOfferRequest(offer: String, did: String)

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
    abstract fun getHistory(limit: Int = 10, offset: Int = 0): List<WalletOperationHistory>
    abstract suspend fun addOperationHistory(operationHistory: WalletOperationHistory)

    // Web3 wallets
    abstract suspend fun linkWallet(wallet: WalletDataTransferObject): LinkedWalletDataTransferObject
    abstract suspend fun unlinkWallet(wallet: UUID): Boolean
    abstract suspend fun getLinkedWallets(): List<LinkedWalletDataTransferObject>
    abstract suspend fun connectWallet(walletId: UUID): Boolean
    abstract suspend fun disconnectWallet(wallet: UUID): Boolean

    // Issuers TODO: move each such component to use-case
    abstract suspend fun listIssuers(): List<IssuerDataTransferObject>
    abstract suspend fun getIssuer(name: String): IssuerDataTransferObject
    abstract fun getCredentialsByIds(credentialIds: List<String>): List<WalletCredential>


    // TODO: Push

}
