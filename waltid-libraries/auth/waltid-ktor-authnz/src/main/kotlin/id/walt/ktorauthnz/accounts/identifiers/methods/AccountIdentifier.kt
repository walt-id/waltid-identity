package id.walt.ktorauthnz.accounts.identifiers.methods

import id.walt.ktorauthnz.KtorAuthnzManager
import kotlinx.serialization.Serializable

@Suppress("EqualsOrHashCode")
@Serializable
sealed class AccountIdentifier {

    internal abstract fun identifierName(): String

    val accountIdentifierName
        get() = identifierName()

    override fun toString(): String = "[$accountIdentifierName: ${toDataString()}]"
    abstract fun toDataString(): String

    override fun equals(other: Any?): Boolean {
        if (other !is AccountIdentifier) return false
        if (other.accountIdentifierName != accountIdentifierName) return false
        if (other.hashCode() != hashCode()) return false

        return true
    }

    suspend fun resolveToAccountId() = KtorAuthnzManager.accountStore.lookupAccountUuid(this)

    abstract class AccountIdentifierFactory<T : AccountIdentifier>(val identifierName: String) {
        abstract fun fromAccountIdentifierDataString(dataString: String): T
    }
}

