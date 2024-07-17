package id.walt.sdjwt

import com.nimbusds.jose.*
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import kotlinx.serialization.json.JsonObject

class SimpleJWTCryptoProvider(
    val jwsAlgorithm: JWSAlgorithm,
    private val jwsSigner: JWSSigner?,
    private val jwsVerifier: JWSVerifier?,
) : JWTCryptoProvider {

    override fun sign(payload: JsonObject, keyID: String?, typ: String, headers: Map<String, Any>): String {
        if (jwsSigner == null) {
            throw Exception("No signer available")
        }
        return SignedJWT(
            JWSHeader.Builder(jwsAlgorithm).type(JOSEObjectType(typ)).keyID(keyID).customParams(headers).build(),
            JWTClaimsSet.parse(payload.toString())
        ).also {
            it.sign(jwsSigner)
        }.serialize()
    }

    override fun verify(jwt: String, keyID: String?): JwtVerificationResult {
        if (jwsVerifier == null) {
            throw Exception("No verifier available")
        }
        return JwtVerificationResult(
            SignedJWT.parse(jwt).verify(jwsVerifier)
        )
    }
}

class SimpleMultiKeyJWTCryptoProvider(
    val providerMap: Map<String, JWTCryptoProvider>
): JWTCryptoProvider {
    override fun sign(payload: JsonObject, keyID: String?, typ: String, headers: Map<String, Any>): String {
        return (providerMap[keyID] ?: throw Exception("No key ID defined")).sign(payload, keyID, typ, headers)
    }

    override fun verify(jwt: String, keyID: String?): JwtVerificationResult {
        return (providerMap[keyID ?: SignedJWT.parse(jwt).header.keyID] ?: throw Exception("No key ID defined")).verify(jwt)
    }

}

