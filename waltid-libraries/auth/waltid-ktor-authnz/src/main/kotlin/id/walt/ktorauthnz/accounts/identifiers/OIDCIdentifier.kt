package id.walt.ktorauthnz.accounts.identifiers

@Suppress("EqualsOrHashCode") // equals provided by AccountIdentifier
class OIDCIdentifier(val host: String, val name: String) : AccountIdentifier("oidc") {
    override fun getString() = name

    override fun hashCode(): Int = 31 * host.hashCode() + name.hashCode()

}
