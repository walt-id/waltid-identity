package id.walt.ktorauthnz.accounts.identifiers

import id.walt.ktorauthnz.KtorAuthnzManager

@Suppress("EqualsOrHashCode")
abstract class AccountIdentifier(val identifierName: String) {

    override fun toString(): String = "[$identifierName: ${getString()}]"
    abstract fun getString(): String

    abstract override fun hashCode(): Int

    override fun equals(other: Any?): Boolean {
        if (other !is AccountIdentifier) return false
        if (other.identifierName != identifierName) return false
        if (other.hashCode() != hashCode()) return false

        return true
    }

    fun resolveToAccountId() = KtorAuthnzManager.accountStore.lookupAccountUuid(this)
}

