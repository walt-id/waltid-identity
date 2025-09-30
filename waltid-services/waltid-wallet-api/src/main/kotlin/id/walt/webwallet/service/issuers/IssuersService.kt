@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.service.issuers

import id.walt.webwallet.db.models.WalletIssuers
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
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
        WalletIssuers.upsert {
            it[this.wallet] = wallet.toJavaUuid()
            it[this.did] = did
            it[this.description] = description
            it[this.uiEndpoint] = uiEndpoint
            it[this.configurationEndpoint] = configurationEndpoint
            it[this.authorized] = authorized
        }
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

    //TODO: copied from CredentialsService
    private fun updateColumn(wallet: Uuid, issuer: String, update: (statement: UpdateStatement) -> Unit): Int =
        WalletIssuers.update({ WalletIssuers.wallet eq wallet.toJavaUuid() and (WalletIssuers.did eq issuer) }) {
            update(it)
        }
}
