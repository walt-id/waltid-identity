package id.walt.openid4vci.pkce

/** Implements one explicitly configured, case-sensitive transformation and its additional format checks. */
interface PkceCodeChallengeMethod {
    val name: String
    fun isValidChallenge(codeChallenge: String): Boolean
    fun calculateChallenge(codeVerifier: String): String
}

object S256PkceCodeChallengeMethod : PkceCodeChallengeMethod {
    override val name: String = "S256"

    override fun isValidChallenge(codeChallenge: String): Boolean =
        codeChallenge.length == 43 && codeChallenge.all {
            it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_'
        }

    override fun calculateChallenge(codeVerifier: String): String = Pkce.generateS256CodeChallenge(codeVerifier)
}
