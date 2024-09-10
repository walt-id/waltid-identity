@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.service.issuers

import id.walt.webwallet.db.models.WalletIssuers
import id.walt.commons.temp.UuidSerializer
import kotlinx.serialization.Serializable

import org.jetbrains.exposed.sql.ResultRow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

@Serializable
data class IssuerDataTransferObject(
    @Serializable(with = UuidSerializer::class) // required to serialize Uuid, until kotlinx.serialization uses Kotlin 2.1.0
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

    companion object {
        fun default(wallet: Uuid) = IssuerDataTransferObject(
            wallet = wallet,
            did = "did:web:walt.id",
            description = "walt.id issuer portal",
            uiEndpoint = "https://portal.walt.id/credentials?ids=",
            configurationEndpoint = "https://issuer.portal.walt.id/.well-known/openid-credential-issuer",
            authorized = false,
        )
    }
}
