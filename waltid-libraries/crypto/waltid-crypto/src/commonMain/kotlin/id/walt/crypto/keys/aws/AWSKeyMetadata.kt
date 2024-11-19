package id.walt.crypto.keys.aws

import kotlinx.serialization.Serializable


@Serializable
data class AWSKeyMetadata(
    val auth: AWSAuth,
)

