package id.walt.ssikit.verification.policies

import id.walt.ssikit.schemes.JwsSignatureScheme
import id.walt.ssikit.verification.JwtVerificationPolicy

class JwtSignaturePolicy : JwtVerificationPolicy(
    "signature",
    "Checks a JWT credential by verifying its cryptographic signature using the key referenced by the DID in `iss`."
) {
    override suspend fun verify(credential: String, args: Any?, context: Map<String, Any>): Result<Any> {
        return JwsSignatureScheme().verify(credential)
    }
}
