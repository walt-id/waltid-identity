package id.walt.credentials.verification.policies

import id.walt.credentials.schemes.JwsSignatureScheme
import id.walt.credentials.verification.JwtVerificationPolicy

class JwtSignaturePolicy : JwtVerificationPolicy(
    "signature",
    "Checks a JWT credential by verifying its cryptographic signature using the key referenced by the DID in `iss`.",
    listOf(VerificationPolicyArgumentType.NONE)
) {
    override suspend fun verify(credential: String, args: Any?, context: Map<String, Any>): Result<Any> {
        return JwsSignatureScheme().verify(credential)
    }
}
