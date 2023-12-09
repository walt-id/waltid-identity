package id.walt.service

import id.walt.db.models.Web3Wallets
import id.walt.service.dto.LinkedWalletDataTransferObject
import id.walt.service.dto.WalletDataTransferObject
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

object Web3WalletService {
    /**
     * Adds the wallet to the given account
     * @param accountId the account's uuid
     * @param wallet the [WalletDataTransferObject]
     * @return the [LinkedWalletDataTransferObject] representing the web3 wallet
     */
    fun link(accountId: UUID, wallet: WalletDataTransferObject): LinkedWalletDataTransferObject =
        Web3Wallets.insert {
            it[account] = accountId
            it[id] = UUID.generateUUID()
            it[address] = wallet.address
            it[ecosystem] = wallet.ecosystem
            it[owner] = false
        }.let { LinkedWalletDataTransferObject(accountId, wallet.address, wallet.ecosystem, false) }

    /**
     * Removes the wallet from the given account
     * @param accountId the account's [UUID]
     * @param walletId the wallet's [UUID]
     * @return true - if operation succeeded, false - otherwise
     */
    fun unlink(accountId: UUID, walletId: UUID): Boolean = transaction {
        Web3Wallets.deleteWhere { (account eq accountId) and (id eq walletId) }
    } == 1

    /**
     * Connects the given wallet to the account,
     * thus allowing access to account data when logging in with wallet
     * @param accountId the account's [UUID]
     * @param walletId the [WalletDataTransferObject]
     * @return true - if operation succeeded, false - otherwise
     */
    fun connect(accountId: UUID, walletId: UUID): Boolean = setIsOwner(accountId, walletId, true) == 1

    /**
     * Resets the owner property for the given account
     * @param accountId the account's [UUID]
     * @param walletId the wallet's [UUID]
     * @return true - if operation succeeded, false - otherwise
     */
    fun disconnect(accountId: UUID, walletId: UUID): Boolean = setIsOwner(accountId, walletId, false) == 1

    /**
     * Fetches the wallets for a given account
     * @param accountId the account's [UUID]
     * @return A list of [LinkedWalletDataTransferObject]s
     */
    fun getLinked(accountId: UUID) =
        /** TODO:
         * include accounts created from wallet-addresses
         * which are owners for 'accountId' (i.e. account-wallet pairs where wallet is owner)
         * e.g.
         * 1. registers email
         * 2. links web3-address
         * 3. registers web3-address (a separate account is going to be created as web3-address not connected)
         * 4. connects web3-address when logged in with email at p.1
         * === 2 different accounts exist, which the user should have access to (merged) when logging in with web3-address
         */
        transaction {
            Web3Wallets.select { Web3Wallets.account eq accountId }.map {
                LinkedWalletDataTransferObject(
                    it[Web3Wallets.id],
                    it[Web3Wallets.address],
                    it[Web3Wallets.ecosystem],
                    it[Web3Wallets.owner]
                )
            }
        }

    private fun setIsOwner(accountId: UUID, walletId: UUID, isOwner: Boolean) = transaction {
        Web3Wallets.update(
            { (Web3Wallets.account eq accountId) and (Web3Wallets.id eq walletId) }
        ) {
            it[owner] = isOwner
        }
    }
}
