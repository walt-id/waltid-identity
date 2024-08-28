package id.walt.authkit.accounts.identifiers

class UsernameIdentifier(val name: String) : AccountIdentifier("username") {
    override fun getString() = name
    override fun hashCode(): Int = name.hashCode()
}
