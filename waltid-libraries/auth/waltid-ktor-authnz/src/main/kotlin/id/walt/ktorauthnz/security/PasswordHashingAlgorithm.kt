package id.walt.ktorauthnz.security

import com.password4j.*
import kotlin.reflect.jvm.jvmName

enum class PasswordHashingAlgorithm(val algorithmInstance: Lazy<AbstractHashingFunction>) {
    ARGON2(lazy { AlgorithmFinder.getArgon2Instance() }),
    PBKDF2(lazy { AlgorithmFinder.getPBKDF2Instance() }),
    PBKDF2_COMPRESSED(lazy { AlgorithmFinder.getCompressedPBKDF2Instance() }),
    BCRYPT(lazy { AlgorithmFinder.getBcryptInstance() }),
    SCRYPT(lazy { AlgorithmFinder.getScryptInstance() }),
    BALLON_HASHING(lazy { AlgorithmFinder.getBalloonHashingInstance() }),
    MESSAGE_DIGEST(lazy { AlgorithmFinder.getMessageDigestInstance() }),
    NONE(lazy { NoneHashFunction });

    companion object {
        fun getByHashingFunction(func: HashingFunction): PasswordHashingAlgorithm = when (func) {
            is Argon2Function -> ARGON2
            is CompressedPBKDF2Function -> PBKDF2_COMPRESSED
            is PBKDF2Function -> PBKDF2
            is BcryptFunction -> BCRYPT
            is ScryptFunction -> SCRYPT
            is BalloonHashingFunction -> BALLON_HASHING
            is MessageDigestFunction -> MESSAGE_DIGEST
            is NoneHashFunction -> NONE
            else -> throw NotImplementedError("Unknown function: ${func::class.jvmName}")
        }
    }
}
