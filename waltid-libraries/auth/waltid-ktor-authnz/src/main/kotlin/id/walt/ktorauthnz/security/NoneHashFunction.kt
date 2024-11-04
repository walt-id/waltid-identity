package id.walt.ktorauthnz.security

import com.password4j.AbstractHashingFunction
import com.password4j.Hash

object NoneHashFunction : AbstractHashingFunction() {
    override fun hash(plainTextPassword: CharSequence?): Hash =
        plainTextPassword.toString().let { Hash(this, it, it.toByteArray(), ByteArray(0)) }

    override fun hash(plainTextPassword: ByteArray?): Hash = Hash(this, plainTextPassword, plainTextPassword, ByteArray(0))
    override fun hash(plainTextPassword: CharSequence?, salt: String?): Hash =
        plainTextPassword.toString().let { Hash(this, it, it.toByteArray(), salt?.toByteArray()) }

    override fun hash(plainTextPassword: ByteArray?, salt: ByteArray?): Hash = Hash(this, plainTextPassword, plainTextPassword, salt)
    override fun check(plainTextPassword: CharSequence?, hashed: String?): Boolean = plainTextPassword == hashed
    override fun check(plainTextPassword: ByteArray?, hashed: ByteArray?): Boolean = plainTextPassword.contentEquals(hashed)
}
