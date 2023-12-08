package id.walt.service.dto

import kotlinx.serialization.Serializable

@Serializable
data class NftListDataTransferObject(
    val items: List<NftDetailDataTransferObject>
)

data class NftConvertResult(
    val id: String,
    var name: String? = null,
    var description: String? = null,
    val contract: String? = null,
    val type: String? = null,
    val attributes: List<TokenAttributes>? = null,
    val art: TokenArt? = null,
) {
    companion object {
        fun NftConvertResult.toDataTransferObject(chain: String) = NftDetailDataTransferObject(
            id = this.id,
            name = this.name,
            description = this.description,
            contract = this.contract,
            type = this.type,
            attributes = this.attributes,
            art = this.art,
            chain = chain,
        )
    }
}

@Serializable
data class NftDetailDataTransferObject(
    val id: String,
    var name: String? = null,
    var description: String? = null,
    val contract: String? = null,
    val type: String? = null,
    val attributes: List<TokenAttributes>? = null,
    val art: TokenArt? = null,
    val chain: String
)

@Serializable
data class TokenAttributes(
    val trait: String,
    var value: String,
)

@Serializable
data class TokenArt(
    var url: String? = null,
    var imageData: String? = null,
)