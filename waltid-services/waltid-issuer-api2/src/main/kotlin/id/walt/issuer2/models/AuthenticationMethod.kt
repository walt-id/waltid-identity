package id.walt.issuer2.models

import kotlinx.serialization.Serializable

@Serializable
enum class AuthenticationMethod {
    PRE_AUTHORIZED,
    AUTHORIZED,
}

@Serializable
enum class IssuerStateMode {
    INCLUDE,
    OMIT,
}

@Serializable
enum class CredentialOfferValueMode {
    BY_REFERENCE,
    BY_VALUE,
}
