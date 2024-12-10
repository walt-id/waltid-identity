package id.walt.crypto.keys.aws

import kotlinx.serialization.Serializable


@Serializable
data class AWSKeyMetadata(
    val auth: AWSAuth,
) {
    constructor(
        accessKeyId: String? = null,
        secretAccessKey: String? = null,
        region: String? = null,
        roleName: String? = null,
        roleArn: String? = null
    ) : this(AWSAuth(accessKeyId, secretAccessKey, region, roleName, roleArn))
}

