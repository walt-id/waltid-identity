package id.walt.webwallet.service.issuers

import id.walt.webwallet.db.models.WalletIssuers
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction

object IssuersService {
    fun get(wallet: UUID, did: String): IssuerDataTransferObject? = transaction {
        queryIssuer(wallet, did)
    }

    fun list(wallet: UUID): List<IssuerDataTransferObject> = transaction {
        WalletIssuers.selectAll().where { WalletIssuers.wallet eq wallet }.map {
            IssuerDataTransferObject(it)
        }
    }

    fun add(
        wallet: UUID,
        did: String,
        description: String?,
        uiEndpoint: String,
        configurationEndpoint: String,
        authorized: Boolean = false,
    ) = transaction {
        addToWalletQuery(wallet, did, description, uiEndpoint, configurationEndpoint, authorized)
    }.insertedCount

    fun authorize(wallet: UUID, issuer: String) = transaction {
        updateColumn(wallet, issuer) {
            it[WalletIssuers.authorized] = true
        }
    }

    private fun queryIssuer(wallet: UUID, did: String) =
        WalletIssuers.selectAll().where { WalletIssuers.wallet eq wallet and (WalletIssuers.did eq did) }
            .singleOrNull()?.let {
                IssuerDataTransferObject(it)
            }

    private fun addToWalletQuery(
        wallet: UUID,
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
        it[this.wallet] = wallet
        it[this.did] = did
        it[this.description] = description
        it[this.uiEndpoint] = uiEndpoint
        it[this.configurationEndpoint] = configurationEndpoint
        it[this.authorized] = authorized
    }

    //TODO: copied from CredentialsService
    private fun updateColumn(wallet: UUID, issuer: String, update: (statement: UpdateStatement) -> Unit): Int =
        WalletIssuers.update({ WalletIssuers.wallet eq wallet and (WalletIssuers.did eq issuer) }) {
            update(it)
        }
}
