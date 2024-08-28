package id.walt.authkit.accounts.identifiers

class EmailIdentifier(val email: String) : AccountIdentifier("email") {
    override fun getString() = email

    override fun hashCode(): Int = email.hashCode()
}
