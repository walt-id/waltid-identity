package id.walt.crypto2.kms

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class CredentialReference(val value: String) {
    init {
        require(value.isNotBlank()) { "Credential reference cannot be blank" }
    }
}
