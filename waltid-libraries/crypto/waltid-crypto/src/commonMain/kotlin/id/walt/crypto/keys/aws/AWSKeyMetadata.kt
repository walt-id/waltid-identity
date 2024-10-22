package id.walt.crypto.keys.aws

import kotlinx.serialization.Serializable


@Serializable
data class AWSKeyMetadata(
    val accessKeyId: String,
    val secretAccessKey: String,
    val region: String
)

