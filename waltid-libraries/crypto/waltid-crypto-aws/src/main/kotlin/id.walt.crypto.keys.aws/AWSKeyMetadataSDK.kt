package id.walt.crypto.keys.aws

import kotlinx.serialization.Serializable


@Serializable
data class AWSKeyMetadataSDK(
    val auth: AwsSDKAuth,
    val keyName: String? = null,
    val tags: Map<String, String>? = null,
    /** Enable multi-region key for disaster recovery support. See: https://docs.aws.amazon.com/kms/latest/developerguide/multi-region-keys-overview.html */
    val multiRegion: Boolean? = null,
    /** Target regions for replica keys. Only used when multiRegion=true. Replicas share the same key material and key ID. */
    val replicaRegions: List<String>? = null,
    /** Enable automatic failover to replica regions when primary region is unavailable. Requires multiRegion=true and replicaRegions. */
    val enableFailover: Boolean? = null,
    /** Custom order of regions to try during failover. If not specified, uses [primary, ...replicaRegions]. */
    val failoverOrder: List<String>? = null,
)


@Serializable
data class AwsSDKAuth(
    val region: String,
)