package id.walt.wallet2.persistence.stores

import app.cash.sqldelight.db.SqlDriver
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.status.CredentialStatusInventory
import kotlin.uuid.Uuid

/** Keeps the foreground opt-in and the worker's inventory cursor in the existing wallet. */
public class SqlDelightStatusMaintenanceStore(
    private val queries: WalletPersistenceQueries,
    private val walletId: String,
) {
    /** @property grantId The current grant, or null for a paused opt-in. */
    public data class Enrollment(public val grantId: String?)

    /** Reads the current opt-in. Null means there is no enrollment. */
    public fun read(): Enrollment? = queries.selectCredentialStatusMaintenance(walletId)
        .executeAsOneOrNull()?.let { Enrollment(it.grant_id) }

    /** Retires the grant and forgets the opt-in and saved cursor. */
    public fun retire() {
        queries.deleteCredentialStatusMaintenance(walletId)
    }

    /** Keeps an existing opt-in while invalidating its grant. */
    public fun pause() {
        queries.transaction {
            if (read()?.grantId != null) queries.setCredentialStatusMaintenance(walletId, null)
        }
    }

    /** Activates consent and creates a grant only when there is no active grant. */
    public fun enable() {
        queries.transaction {
            if (read()?.grantId == null) queries.setCredentialStatusMaintenance(walletId, Uuid.random().toString())
        }
    }

    /** Admission for a cold operation on the existing encrypted database. */
    public companion object {
        /** Admits a status-only view. Use an existing-only driver already opened by the platform. */
        public fun admit(driver: SqlDriver, walletId: String, grantId: String): Session? {
            val queries = WalletPersistenceDatabase(driver).walletPersistenceQueries
            val enrollment = queries.selectCredentialStatusMaintenance(walletId).executeAsOneOrNull()
            if (enrollment?.grant_id != grantId) return null
            return Session(queries, walletId, grantId, enrollment.cursor_id)
        }
    }

    /** All status writes and cursor advances are fenced by this grant. */
    public class Session internal constructor(
        private val queries: WalletPersistenceQueries,
        private val walletId: String,
        private val grantId: String,
        cursor: String?,
    ) {
        /** The last inspected wallet-local ID in this rotation. */
        public var cursor: String? = cursor
            private set

        /** Status input and commit operations for the admitted wallet. */
        public val inventory: CredentialStatusInventory = SqlDelightStatusInventory(queries, ::isAuthorized)

        /** Checks whether this session still belongs to the active grant. */
        public fun isAuthorized(): Boolean =
            queries.selectCredentialStatusMaintenance(walletId).executeAsOneOrNull()?.grant_id == grantId

        /** Advances only while the expected cursor and grant are current. */
        public fun advance(id: String): Boolean = queries.transactionWithResult {
            queries.advanceCredentialStatusMaintenance(id, walletId, grantId, cursor)
            if (queries.credentialStatusChanges().executeAsOne() != 1L) return@transactionWithResult false
            cursor = id
            true
        }

        /** Number of entries remaining after this cursor in the current rotation. */
        public fun remaining(): Int =
            cursor?.let { queries.countStatusInputsAfter(it).executeAsOne().coerceAtMost(Int.MAX_VALUE.toLong()).toInt() } ?: 0
    }
}
