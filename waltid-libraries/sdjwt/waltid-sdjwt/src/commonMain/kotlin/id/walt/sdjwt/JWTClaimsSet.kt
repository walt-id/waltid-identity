package id.walt.sdjwt

/**
 * Expected class for JWT claim set in platform specific implementation. Not necessarily required.
 */
expect class JWTClaimsSet {
    override fun toString(): String
}
