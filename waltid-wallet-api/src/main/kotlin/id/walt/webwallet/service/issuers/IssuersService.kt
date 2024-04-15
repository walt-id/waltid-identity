package id.walt.webwallet.service.issuers

import id.walt.webwallet.db.models.WalletIssuers
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.jetbrains.exposed.sql.transactions.transaction

object IssuersService {
    fun get(wallet: UUID, name: String): IssuerDataTransferObject? = transaction {
        queryIssuer(wallet, name)
    }

    fun list(wallet: UUID): List<IssuerDataTransferObject> = transaction {
        WalletIssuers.selectAll().where { WalletIssuers.wallet eq wallet }.map {
            IssuerDataTransferObject(it)
        }
    }

    fun add(wallet: UUID, name: String, description: String?, uiEndpoint: String, configurationEndpoint: String) = transaction {
        addToWalletQuery(wallet, name, description, uiEndpoint, configurationEndpoint)
    }.insertedCount

    fun authorize(wallet: UUID, issuer: String) = transaction {
        updateColumn(wallet, issuer) {
            it[WalletIssuers.authorized] = true
        }
    }

    private fun queryIssuer(wallet: UUID, name: String) =
        WalletIssuers.selectAll().where { WalletIssuers.wallet eq wallet and (WalletIssuers.name eq name) }
            .singleOrNull()?.let {
                IssuerDataTransferObject(it)
            }

    private fun addToWalletQuery(
        wallet: UUID,
        name: String,
        description: String?,
        uiEndpoint: String,
        configurationEndpoint: String,
        authorized: Boolean = false
    ) = WalletIssuers.upsert(
        keys = arrayOf(WalletIssuers.wallet, WalletIssuers.name),
        onUpdate = listOf(
            WalletIssuers.description to stringLiteral(description ?: ""),
            WalletIssuers.uiEndpoint to stringLiteral(uiEndpoint),
            WalletIssuers.configurationEndpoint to stringLiteral(configurationEndpoint),
            WalletIssuers.authorized to booleanLiteral(authorized)
        )
    ) {
        it[this.wallet] = wallet
        it[this.name] = name
        it[this.description] = description
        it[this.uiEndpoint] = uiEndpoint
        it[this.configurationEndpoint] = configurationEndpoint
        it[this.authorized] = authorized
    }

    //TODO: copied from CredentialsService
    private fun updateColumn(wallet: UUID, issuer: String, update: (statement: UpdateStatement) -> Unit): Int =
        WalletIssuers.update({ WalletIssuers.wallet eq wallet and (WalletIssuers.name eq issuer) }) {
            update(it)
        }
}
