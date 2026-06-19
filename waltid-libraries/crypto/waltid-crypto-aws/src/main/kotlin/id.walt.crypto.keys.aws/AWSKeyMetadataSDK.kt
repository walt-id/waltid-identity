package id.walt.crypto.keys.aws

import kotlinx.serialization.Serializable


@Serializable
data class AWSKeyMetadataSDK(
    val auth: AwsSDKAuth,
    val keyName: String? = null,
    val tags: Map<String, String>? = null,
    /** Enable multi-region key for disaster recovery support. See: https://docs.aws.amazon.com/kms/latest/developerguide/multi-region-keys-overview.html */
    val multiRegion: Boolean? = null,
)


@Serializable
data class AwsSDKAuth(
    val region: String,
)