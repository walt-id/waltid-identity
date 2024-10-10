package id.walt.crypto.keys

import kotlinx.serialization.Serializable

@Serializable
sealed class KeyMeta {
    abstract val keyId: String
}

@Serializable
data class OciKeyMeta(
    override val keyId: String,
    val keyVersion: String,
) : KeyMeta()

@Serializable
data class TseKeyMeta(
    override val keyId: String,
) : KeyMeta()

@Serializable
data class JwkKeyMeta(
    override val keyId: String,
    val keySize: Int? = null,
) : KeyMeta()

@Serializable
data class AwsKeyMeta(
    override val keyId: String,
    val keySize: Int? = null,
) : KeyMeta()