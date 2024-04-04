package id.walt.crypto.keys

import kotlinx.serialization.Serializable

sealed class KeyMeta {
    abstract val keyId: String
}

@Serializable
data class OciKeyMeta(
    override val keyId: String,
    val keyVersion: String,
) : KeyMeta()

data class TseKeyMeta(
    override val keyId: String,
) : KeyMeta()

data class JwkKeyMeta(
    override val keyId: String,
    val keySize: Int? = null,
) : KeyMeta()