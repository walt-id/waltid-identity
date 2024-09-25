package id.walt.ktorauthnz.accounts.identifiers

@Suppress("EqualsOrHashCode") // equals provided by AccountIdentifier
class EmailIdentifier(val email: String) : AccountIdentifier("email") {
    override fun getString() = email

    override fun hashCode(): Int = email.hashCode()
}
