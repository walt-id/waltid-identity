package id.walt.service.dto

import id.walt.db.models.Web3Wallets
import kotlinx.serialization.Serializable
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.ResultRow

@Serializable
data class LinkedWalletDataTransferObject(
    val id: UUID,
    val address: String,
    val ecosystem: String,
    val owner: Boolean,
) {

    constructor(result: ResultRow) : this(
        id = result[Web3Wallets.id],
        address = result[Web3Wallets.address],
        ecosystem = result[Web3Wallets.ecosystem],
        owner = result[Web3Wallets.owner],
    )

}
