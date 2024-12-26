package id.walt.crypto.keys.aws

import kotlinx.serialization.Serializable


@Serializable
data class AWSKeyMetadataSDK (
    val region: String
)