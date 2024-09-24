package id.walt.authkit.accounts.identifiers

@Suppress("EqualsOrHashCode") // equals provided by AccountIdentifier
class JWTIdentifier(val subject: String) : AccountIdentifier("jwt") {
    override fun getString() = subject

    override fun hashCode(): Int = subject.hashCode()

}
