package id.walt.credentials

interface Claims {
    fun getValue(): String
}

enum class JwtClaims(private val value: String) : Claims {
    NotBefore("nbf"),
    NotAfter("exp"),
    IssuedAt("iat");

    override fun getValue() = value
}

enum class VcClaims {;

    enum class V1(private val value: String) : Claims {
        NotBefore("issuanceDate"),
        NotAfter("expirationDate");

        override fun getValue() = value
    }

    enum class V2(private val value: String) : Claims {
        NotBefore("validFrom"),
        NotAfter("validUntil");

        override fun getValue() = value
    }
}