package id.walt.credentials

import kotlinx.serialization.Serializable

@Serializable
sealed interface Claims {
    fun getValue(): String
}

@Serializable
enum class JwtClaims(private val value: String) : Claims {
    NotBefore("nbf"),
    NotAfter("exp"),
    IssuedAt("iat");

    override fun getValue() = value
}

@Serializable
enum class VcClaims {;

    @Serializable
    enum class V1(private val value: String) : Claims {
        NotBefore("issuanceDate"),
        NotAfter("expirationDate");

        override fun getValue() = value
    }

    @Serializable
    enum class V2(private val value: String) : Claims {
        NotBefore("validFrom"),
        NotAfter("validUntil");

        override fun getValue() = value
    }
}
