package id.walt.openid4vci.pkce

/** Method registry and request-scoped requirement shared by the OAuth provider's PKCE checks. */
class PkceServiceConfig(
    methods: List<PkceCodeChallengeMethod> = listOf(S256PkceCodeChallengeMethod),
    /** Evaluated when validating an authorization request or redeeming a code issued without PKCE. */
    val requirePkce: suspend () -> Boolean = { false },
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
