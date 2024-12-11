package id.walt.ktorauthnz.accounts.identifiers.methods

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("web3")
data class Web3Identifier(
    val publicKey: String
) : AccountIdentifier() {
    override fun identifierName() = "multistep-example" // SerialName and identifierName should match

    override fun toDataString() = publicKey // what is part of this identifier? In this case just the string "publicKey"

    companion object :
        AccountIdentifierFactory<Web3Identifier>("multistep-example") { // this creator id also has to match with identifierName
        override fun fromAccountIdentifierDataString(dataString: String) = Web3Identifier(dataString)

        val EXAMPLE = Web3Identifier("0xABCDEF0123456789") // Define a nice example for the docs
    }
}
