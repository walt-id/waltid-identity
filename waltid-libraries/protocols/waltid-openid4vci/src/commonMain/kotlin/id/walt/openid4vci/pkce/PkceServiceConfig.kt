package id.walt.openid4vci.pkce

/** Immutable method registry shared by runtime validation and authorization-server metadata. */
class PkceServiceConfig(
    methods: List<PkceCodeChallengeMethod> = listOf(S256PkceCodeChallengeMethod),
    val requirePkce: Boolean = false,
) {
    val methods: List<PkceCodeChallengeMethod> = methods.toList()
    val supportedCodeChallengeMethods: List<String> = this.methods.map { it.name }

    init {
        require(this.methods.isNotEmpty()) { "At least one PKCE method must be configured" }
        require(supportedCodeChallengeMethods.none { it.isBlank() }) { "PKCE method names must not be blank" }
        require(supportedCodeChallengeMethods.distinct().size == this.methods.size) {
            "Duplicate PKCE method configured"
        }
    }
}
