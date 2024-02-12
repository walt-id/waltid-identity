package id.walt.credentials.verification

abstract class JwtVerificationPolicy(
    override val name: String,
    override val description: String? = null,
    override val argumentTypes: List<VerificationPolicyArgumentType>? = null
) : VerificationPolicy(name, description, "jwt-verifier", argumentTypes) {

    abstract suspend fun verify(credential: String, args: Any? = null, context: Map<String, Any>): Result<Any>
}
