package id.walt.ktorauthnz.accounts.identifiers

@Suppress("EqualsOrHashCode") // equals provided by AccountIdentifier
class RADIUSIdentifier(val host: String, val name: String) : AccountIdentifier("radius") {
    override fun getString() = name

    override fun hashCode(): Int = 31 * host.hashCode() + name.hashCode()

}
