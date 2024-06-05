package id.walt.sdjwt

import com.nimbusds.jose.*
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import kotlinx.serialization.json.JsonObject

class SimpleJWTCryptoProvider(
    val jwsAlgorithm: JWSAlgorithm,
    private val jwsSigner: JWSSigner?,
    private val jwsVerifier: JWSVerifier?
) : JWTCryptoProvider {

    override fun sign(payload: JsonObject, keyID: String?, typ: String): String {
        if (jwsSigner == null) {
            throw Exception("No signer available")
        }
        return SignedJWT(
            JWSHeader.Builder(jwsAlgorithm).type(JOSEObjectType.JWT).keyID(keyID).build(),
            JWTClaimsSet.parse(payload.toString())
        ).also {
            it.sign(jwsSigner)
        }.serialize()
    }

    override fun verify(jwt: String): JwtVerificationResult {
        if (jwsVerifier == null) {
            throw Exception("No verifier available")
        }
        return JwtVerificationResult(
            SignedJWT.parse(jwt).verify(jwsVerifier)
        )
    }
}

