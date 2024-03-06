package id.walt.webwallet.utils

import com.auth0.jwk.Jwk
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import id.walt.webwallet.service.OidcLoginService
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey

object JwkUtils {

    fun Jwk.makeAlgorithm(): Algorithm = when (algorithm) {
        "RS256" -> Algorithm.RSA256(publicKey as RSAPublicKey, null)
        "RS384" -> Algorithm.RSA384(publicKey as RSAPublicKey, null)
        "RS512" -> Algorithm.RSA512(publicKey as RSAPublicKey, null)
        "ES256" -> Algorithm.ECDSA256(publicKey as ECPublicKey, null)
        "ES384" -> Algorithm.ECDSA384(publicKey as ECPublicKey, null)
        "ES512" -> Algorithm.ECDSA512(publicKey as ECPublicKey, null)
        null -> Algorithm.RSA256(publicKey as RSAPublicKey, null)
        else -> throw IllegalArgumentException("Unsupported algorithm $algorithm")
    }

    fun verifyToken(token: String): DecodedJWT {
        val decoded = JWT.decode(token)

        val verifier = JWT.require(OidcLoginService.jwkProvider.get(decoded.keyId).makeAlgorithm())
            .withIssuer(OidcLoginService.oidcRealm)
            .build()

        return verifier.verify(decoded)!!
    }

}
