package id.walt.ktorauthnz.accounts.identifiers.methods

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("web3")
data class Web3Identifier(
    val address: String
) : AccountIdentifier() {
    override fun identifierName() = "web3"

    override fun toDataString() = address

    companion object : AccountIdentifierFactory<Web3Identifier>("web3") {
        override fun fromAccountIdentifierDataString(dataString: String) = Web3Identifier(dataString)

        val EXAMPLE = Web3Identifier("0xABCDEF0123456789")
    }
}
