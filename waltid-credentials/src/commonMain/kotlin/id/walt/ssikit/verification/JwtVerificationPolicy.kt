package id.walt.ssikit.verification

abstract class JwtVerificationPolicy(override val name: String, override val description: String? = null) : VerificationPolicy(name, description) {

    abstract suspend fun verify(credential: String, args: Any? = null, context: Map<String, Any>): Result<Any>

}
