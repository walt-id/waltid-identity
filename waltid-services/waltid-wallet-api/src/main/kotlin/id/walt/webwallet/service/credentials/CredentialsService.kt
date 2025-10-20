@file:OptIn(ExperimentalTime::class)

package id.walt.webwallet.service.credentials

import id.walt.webwallet.db.models.WalletCategory
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.db.models.WalletCredentialCategoryMap
import id.walt.webwallet.db.models.WalletCredentials
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
class CredentialsService {
    val categoryService = CategoryService()

    private val notDeletedItemsCondition = WalletCredentials.deletedOn eq null
    private val deletedItemsCondition = WalletCredentials.deletedOn neq null

    /**
     * Returns a credential identifier by [credentialId]
     * @param wallet wallet id
     * @param credentialId credential id
     * @return [WalletCredential] or null, it not found
     */
    fun get(wallet: Uuid, credentialId: String): WalletCredential? =
        transaction {
            getCredentialsQuery(wallet, true, listOf(credentialId))
                .singleOrNull()?.let { WalletCredential(it) }
        }

    /**
     * Returns a list of credentials identified by the [credentialIdList]
     * @param credentialIdList the list of credential ids
     * @return list of [WalletCredential] that could match the specified [credentialIdList]
     */
    fun get(wallet: Uuid, credentialIdList: List<String>): List<WalletCredential> = transaction {
        getCredentialsQuery(wallet, false, credentialIdList)
            .map {
                WalletCredential(it)
            }
    }

    /**
     * Returns a list of filtered credentials
     * @param wallet wallet id
     * @param filter the credential filter
     * @return list of [WalletCredential] matching the filter conditions
     */
    fun list(wallet: Uuid, filter: CredentialFilterObject) = transaction {
        let {
            filter.categories?.let {
                it.takeIf { it.isEmpty() }?.let {
                    uncategorizedQuery(wallet, filter.showDeleted, filter.showPending)
                } ?: categorizedQuery(wallet, filter.showDeleted, filter.showPending, it)
            } ?: allQuery(wallet, filter.showDeleted, filter.showPending)
        }.orderBy(
            column = WalletCredentials.addedOn, order = if (filter.sorDescending) SortOrder.DESC else SortOrder.ASC
        ).distinctBy { it[WalletCredentials.id] }.map { WalletCredential(it) }
    }

    /**
     * Stores the specified credentials
     * @param wallet wallet id
     * @param credentials the credential object
     * @return list of credential id
     */
    fun add(wallet: Uuid, vararg credentials: WalletCredential) = addAll(wallet, credentials.toList())

    /**
     * Deletes a credential
     * @param wallet wallet id
     * @param credentialId credential id
     * @param permanent flag a permanent delete
     * @return True if deleted successfully, false - otherwise
     */
    fun delete(wallet: Uuid, credentialId: String, permanent: Boolean): Boolean = (permanent.takeIf {
        it
    }?.let {
        deleteCredential(wallet, credentialId)
    } ?: updateDelete(wallet, credentialId, true)) > 0

    /**
     * Attempts to restore a temporarily deleted credential
     * @param wallet wallet id
     * @param credentialId credential id
     * @return the [WalletCredential] object if restore succeeded, null otherwise
     */
    fun restore(wallet: Uuid, credentialId: String) = get(wallet, credentialId)?.also {
        updateDelete(wallet, credentialId, false)
    }

    fun setPending(wallet: Uuid, credentialId: String, pending: Boolean = true) =
        updatePending(wallet, credentialId, pending)

    private fun addAll(wallet: Uuid, credentials: List<WalletCredential>): List<String> = transaction {
        WalletCredentials.batchInsert(credentials) { credential: WalletCredential ->
            this[WalletCredentials.wallet] = wallet.toJavaUuid()
            this[WalletCredentials.id] = credential.id
            this[WalletCredentials.document] = credential.document
            this[WalletCredentials.disclosures] = credential.disclosures
            this[WalletCredentials.addedOn] = Clock.System.now().toJavaInstant()
            this[WalletCredentials.manifest] = credential.manifest
            this[WalletCredentials.pending] = credential.pending
            this[WalletCredentials.format] = credential.format.value
        }.map { it[WalletCredentials.id] }
    }

    private fun getCredentialsQuery(wallet: Uuid, includeDeleted: Boolean, credentialIdList: List<String>) =
        WalletCredentials.selectAll().where {
            (WalletCredentials.wallet eq wallet.toJavaUuid()) and (WalletCredentials.id inList credentialIdList and (notDeletedItemsCondition or (includeDeleted.takeIf { it }
                ?.let { Op.TRUE } ?: Op.FALSE)))
        }

    private fun updateDelete(wallet: Uuid, credentialId: String, value: Boolean): Int = transaction {
        updateColumn(wallet, credentialId) {
            it[WalletCredentials.deletedOn] = value.takeIf { it }?.let { Instant.now() }
        }
    }

    private fun updatePending(wallet: Uuid, credentialId: String, value: Boolean): Int = transaction {
        updateColumn(wallet, credentialId) {
            it[WalletCredentials.pending] = value
        }
    }

    //TODO: copied from IssuersService
    private fun updateColumn(wallet: Uuid, credentialId: String, update: (statement: UpdateStatement) -> Unit): Int =
        WalletCredentials.update({ WalletCredentials.wallet eq wallet.toJavaUuid() and (WalletCredentials.id eq credentialId) }) {
            update(it)
        }

    private fun deleteCredential(wallet: Uuid, credentialId: String) =
        transaction { WalletCredentials.deleteWhere { (WalletCredentials.wallet eq wallet.toJavaUuid()) and (id eq credentialId) } }

    private fun categorizedQuery(wallet: Uuid, deleted: Boolean?, pending: Boolean?, categories: List<String>) =
        WalletCredentials.innerJoin(
            otherTable = WalletCredentialCategoryMap,
            onColumn = { id },
            otherColumn = { credential },
            additionalConstraint = {
                WalletCredentials.wallet eq wallet.toJavaUuid() and (WalletCredentialCategoryMap.wallet eq wallet) and deletedCondition(
                    deleted
                ) and pendingCondition(pending)
            }).innerJoin(
            otherTable = WalletCategory,
            onColumn = { WalletCredentialCategoryMap.category },
            otherColumn = { id },
            additionalConstraint = {
                WalletCategory.wallet eq wallet.toJavaUuid() and (WalletCredentialCategoryMap.wallet eq wallet) and (WalletCategory.name inList (categories))
            }).selectAll()

    private fun uncategorizedQuery(wallet: Uuid, deleted: Boolean?, pending: Boolean?) =
        WalletCredentials.selectAll().where {
            WalletCredentials.wallet eq wallet.toJavaUuid() and (WalletCredentials.id notInSubQuery (WalletCredentialCategoryMap.select(
                WalletCredentialCategoryMap.credential
            ).where { WalletCredentialCategoryMap.wallet eq wallet })) and deletedCondition(deleted) and pendingCondition(
                pending
            )
        }

    private fun allQuery(wallet: Uuid, deleted: Boolean?, pending: Boolean?) = WalletCredentials.selectAll()
        .where { WalletCredentials.wallet eq wallet.toJavaUuid() and deletedCondition(deleted) and pendingCondition(pending) }

    private fun deletedCondition(deleted: Boolean?) = deleted?.let {
        it.takeIf { it }?.let { deletedItemsCondition } ?: notDeletedItemsCondition
    } ?: Op.TRUE

    private fun pendingCondition(pending: Boolean?) = pending?.let { WalletCredentials.pending eq it } ?: Op.TRUE

    class CategoryService {
        fun add(wallet: Uuid, credentialId: String, vararg category: String): Int = transaction {
            WalletCredentialCategoryMap.batchUpsert(
                getCategoryIds(wallet, category.toList()),
                WalletCredentialCategoryMap.wallet,
                WalletCredentialCategoryMap.credential,
                WalletCredentialCategoryMap.category
            ) {
                this[WalletCredentialCategoryMap.wallet] = wallet
                this[WalletCredentialCategoryMap.credential] = credentialId
                this[WalletCredentialCategoryMap.category] = it
            }.count()
        }

        fun delete(wallet: Uuid, credentialId: String, vararg category: String): Int = transaction {
            WalletCredentialCategoryMap.deleteWhere {
                WalletCredentialCategoryMap.wallet eq wallet and (credential eq credentialId) and (WalletCredentialCategoryMap.category inList (getCategoryIds(
                    wallet, category.toList()
                )))
            }
        }

        private fun getCategoryIds(wallet: Uuid, category: List<String>): List<Int> = transaction {
            WalletCategory.selectAll()
                .where { WalletCategory.wallet eq wallet.toJavaUuid() and (WalletCategory.name inList category.toList()) }.map {
                    it[WalletCategory.id].value
                }
        }
    }
}

@Serializable
data class CredentialFilterObject(
    val categories: List<String>?,
    val showDeleted: Boolean?,
    val showPending: Boolean?,
    val sortBy: String,
    val sorDescending: Boolean,
) {
    companion object {
        val default = CredentialFilterObject(
            categories = null,
            showDeleted = false,
            showPending = false,
            sortBy = "",
            sorDescending = false
        )
    }
}
