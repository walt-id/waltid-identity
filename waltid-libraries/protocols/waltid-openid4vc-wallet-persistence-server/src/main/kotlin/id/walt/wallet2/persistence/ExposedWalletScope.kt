package id.walt.wallet2.persistence

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

/** Durable lifecycle identity shared by a wallet's continuation and credential adapters. */
internal class ExposedWalletScope private constructor(val walletId: String, val generation: String) {
    private val wallets = Wallet2Tables.Wallets

    companion object {
        suspend fun resolve(walletId: String, db: Database): ExposedWalletScope = suspendTransaction(db) {
            val wallets = Wallet2Tables.Wallets
            fun generation() = wallets.selectAll().where { wallets.id eq walletId }
                .singleOrNull()?.get(wallets.generation)
            val current = generation() ?: run {
                wallets.update({ (wallets.id eq walletId) and wallets.generation.isNull() }) {
                    it[wallets.generation] = UUID.randomUUID().toString()
                }
                checkNotNull(generation()) { "Wallet no longer exists" }
            }
            ExposedWalletScope(walletId, current)
        }
    }

    fun isCurrent(): Boolean = wallets.selectAll().where {
        (wallets.id eq walletId) and (wallets.generation eq generation)
    }.any()

    /**
     * Called in the transaction containing the write. The conditional UPDATE locks the parent row
     * until commit, including on PostgreSQL READ COMMITTED and SQLite without foreign keys enabled.
     * Deletion therefore either follows this write or makes it fail; an ID reused later cannot match.
     */
    fun lockCurrent(): Boolean = wallets.update({ (wallets.id eq walletId) and (wallets.generation eq generation) }) {
        it[wallets.generation] = generation
    } == 1

    fun requireCurrentForWrite() {
        check(lockCurrent()) { "Wallet no longer exists or has been recreated" }
    }
}
