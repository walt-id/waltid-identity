package id.walt.ktorauthnz.security

import kotlinx.serialization.Serializable

@Serializable
data class PasswordHash(
    val hash: String,
    val algorithm: PasswordHashingAlgorithm,
) {
    companion object {
        fun fromUpdate(pw4jHash: com.password4j.Hash) =
            PasswordHash(pw4jHash.result, PasswordHashingAlgorithm.getByHashingFunction(pw4jHash.hashingFunction))

        fun fromString(string: String): PasswordHash {
            val i = string.indexOf('/')
            val alg = string.substring(0, i)
            val hash = string.substring(i + 1)
            return PasswordHash(hash, PasswordHashingAlgorithm.valueOf(alg))
        }
    }

    override fun toString(): String = "$algorithm/$hash"
}
