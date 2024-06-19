package id.walt.oid4vc.definitions

object JWTClaims {
    object Payload {
        const val issuer = "iss"
        const val subject = "sub"
        const val audience = "aud"
        const val expirationTime = "exp"
        const val notBeforeTime = "nbf"
        const val issuedAtTime = "iat"
        const val jwtID = "jti"
        const val nonce = "nonce"
    }

    object Header {
        const val algorithm = "alg"
        const val type = "typ"
        const val keyID = "kid"
        const val jwk = "jwk"
        const val x5c = "x5c"
    }
}
