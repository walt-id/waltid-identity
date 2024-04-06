package id.walt.webwallet.service.issuers

import id.walt.webwallet.db.models.WalletIssuers
import kotlinx.serialization.Serializable
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.ResultRow

@Serializable
data class IssuerDataTransferObject(
    val wallet: UUID,
    val name: String,
    val description: String? = "no description",
    val uiEndpoint: String = "",
    val configurationEndpoint: String = "",
    val authorized: Boolean = false,
) {
    constructor(resultRow: ResultRow) : this(
        wallet = resultRow[WalletIssuers.wallet].value,
        name = resultRow[WalletIssuers.name],
        description = resultRow[WalletIssuers.description],
        uiEndpoint = resultRow[WalletIssuers.uiEndpoint],
        configurationEndpoint = resultRow[WalletIssuers.configurationEndpoint],
        authorized = resultRow[WalletIssuers.authorized],
    )

    companion object {
        fun default(wallet: UUID) = IssuerDataTransferObject(
            wallet = wallet,
            name = "walt.id",
            description = "walt.id issuer portal",
            uiEndpoint = "https://portal.walt.id/credentials?ids=",
            configurationEndpoint = "https://issuer.portal.walt.id/.well-known/openid-credential-issuer",
            authorized = false,
        )
    }
}
