@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.service.issuers

import id.walt.webwallet.db.models.WalletIssuers

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

object IssuersService {
    fun get(wallet: Uuid, did: String): IssuerDataTransferObject? = transaction {
        queryIssuer(wallet, did)
    }

    fun list(wallet: Uuid): List<IssuerDataTransferObject> = transaction {
        WalletIssuers.selectAll().where { WalletIssuers.wallet eq wallet.toJavaUuid() }.map {
            IssuerDataTransferObject(it)
        }
    }

    fun add(
        wallet: Uuid,
        did: String,
        description: String?,
        uiEndpoint: String,
        configurationEndpoint: String,
        authorized: Boolean = false,
    ) = transaction {
        addToWalletQuery(wallet, did, description, uiEndpoint, configurationEndpoint, authorized)
    }.insertedCount

    fun authorize(wallet: Uuid, issuer: String) = transaction {
        updateColumn(wallet, issuer) {
            it[WalletIssuers.authorized] = true
        }
    }

    private fun queryIssuer(wallet: Uuid, did: String) =
        WalletIssuers.selectAll().where { WalletIssuers.wallet eq wallet.toJavaUuid() and (WalletIssuers.did eq did) }
            .singleOrNull()?.let {
                IssuerDataTransferObject(it)
            }

    private fun addToWalletQuery(
        wallet: Uuid,
        did: String,
        description: String?,
        uiEndpoint: String,
        configurationEndpoint: String,
        authorized: Boolean,
    ) = WalletIssuers.upsert(
        keys = arrayOf(WalletIssuers.wallet, WalletIssuers.did),
        onUpdate = listOf(
            description?.let { WalletIssuers.description to stringLiteral(it) },
            WalletIssuers.uiEndpoint to stringLiteral(uiEndpoint),
            WalletIssuers.configurationEndpoint to stringLiteral(configurationEndpoint),
            WalletIssuers.authorized to booleanLiteral(authorized)
        ).mapNotNull {
            it
        }
    ) {
        it[this.wallet] = wallet.toJavaUuid()
        it[this.did] = did
        it[this.description] = description
        it[this.uiEndpoint] = uiEndpoint
        it[this.configurationEndpoint] = configurationEndpoint
        it[this.authorized] = authorized
    }

    //TODO: copied from CredentialsService
    private fun updateColumn(wallet: Uuid, issuer: String, update: (statement: UpdateStatement) -> Unit): Int =
        WalletIssuers.update({ WalletIssuers.wallet eq wallet.toJavaUuid() and (WalletIssuers.did eq issuer) }) {
            update(it)
        }
}
