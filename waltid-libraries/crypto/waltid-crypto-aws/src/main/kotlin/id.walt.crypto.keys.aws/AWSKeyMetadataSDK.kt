package id.walt.crypto.keys.aws

import kotlinx.serialization.Serializable


@Serializable
data class AWSKeyMetadataSDK(
    val auth: AwsSDKAuth,
    val keyName: String? = null,
    val tags: Map<String, String>? = null,
)


@Serializable
data class AwsSDKAuth(
    val region: String,
)