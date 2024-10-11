package id.walt.ktorauthnz.accounts.identifiers.methods

import id.walt.ktorauthnz.KtorAuthnzManager
import kotlinx.serialization.Serializable

@Suppress("EqualsOrHashCode")
@Serializable
abstract class AccountIdentifier(val identifierName: String) {

    override fun toString(): String = "[$identifierName: ${toDataString()}]"
    abstract fun toDataString(): String

    override fun equals(other: Any?): Boolean {
        if (other !is AccountIdentifier) return false
        if (other.identifierName != identifierName) return false
        if (other.hashCode() != hashCode()) return false

        return true
    }

    fun resolveToAccountId() = KtorAuthnzManager.accountStore.lookupAccountUuid(this)

    abstract class AccountIdentifierFactory<T : AccountIdentifier>(val identifierName: String) {
        abstract fun fromAccountIdentifierDataString(dataString: String): T
    }
}

