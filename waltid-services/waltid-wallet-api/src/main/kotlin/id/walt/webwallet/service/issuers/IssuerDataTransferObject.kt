@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.service.issuers

import id.walt.webwallet.db.models.WalletIssuers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

@Serializable
data class IssuerDataTransferObject(
    val wallet: Uuid,
    val did: String,
    val description: String? = "no description",
    val uiEndpoint: String = "",
    val configurationEndpoint: String = "",
    val authorized: Boolean = false,
) {
    constructor(resultRow: ResultRow) : this(
        wallet = resultRow[WalletIssuers.wallet].value.toKotlinUuid(),
        did = resultRow[WalletIssuers.did],
        description = resultRow[WalletIssuers.description],
        uiEndpoint = resultRow[WalletIssuers.uiEndpoint],
        configurationEndpoint = resultRow[WalletIssuers.configurationEndpoint],
        authorized = resultRow[WalletIssuers.authorized],
    )
}
