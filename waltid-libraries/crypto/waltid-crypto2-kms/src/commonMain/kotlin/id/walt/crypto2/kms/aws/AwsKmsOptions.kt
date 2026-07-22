package id.walt.crypto2.kms.aws

import id.walt.crypto2.kms.CredentialReference
import id.walt.crypto2.kms.requireHttpEndpoint
import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AwsKmsOptions(
    val region: String,
    val credentialReference: CredentialReference,
    val endpoint: String? = null,
) {
    init {
        require(Regex("[a-z]{2}(?:-[a-z0-9]+)+-[0-9]+").matches(region)) { "AWS region is invalid" }
        endpoint?.let { requireHttpEndpoint(it, "AWS KMS endpoint") }
    }

    fun encode(): BinaryData = BinaryData(json.encodeToString(this).encodeToByteArray())

    internal fun endpointUrl(): String = endpoint ?: "https://kms.$region.amazonaws.com/"

    companion object {
        private val json = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
        }

        internal fun decode(data: BinaryData): AwsKmsOptions = json.decodeFromString(data.toByteArray().decodeToString())
    }
}

class AwsCredentials(
    val accessKeyId: String,
    val secretAccessKey: String,
    val sessionToken: String? = null,
) {
    init {
        require(accessKeyId.isNotBlank()) { "AWS access key ID cannot be blank" }
        require(secretAccessKey.isNotBlank()) { "AWS secret access key cannot be blank" }
        require(sessionToken == null || sessionToken.isNotBlank()) { "AWS session token cannot be blank" }
    }
}

fun interface AwsCredentialResolver {
    suspend fun resolve(reference: CredentialReference): AwsCredentials
}
