package id.walt.crypto.keys

import id.walt.crypto.keys.aws.AWSKeyMetadata
import id.walt.crypto.keys.azure.AzureKeyMetadata
import id.walt.crypto.keys.oci.OCIKeyMetadata
import id.walt.crypto.keys.tse.TSEKeyMetadata
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Type-safe alternative to [KeyGenerationRequest] for use in APIs and UIs.
 *
 * Each subclass carries exactly the metadata required by its backend, with no
 * stringly-typed discriminator field and no untyped [kotlinx.serialization.json.JsonObject]
 * for config. Swagger/OpenAPI renders distinct schemas for each backend variant.
 *
 * [KeyManager.createKey] accepts both this type and the legacy [KeyGenerationRequest].
 *
 * [keyType] defaults to [KeyType.secp256r1] (ES256) - supported by all credential
 * formats including mdoc/ISO 18013-5, which does not support Ed25519.
 *
 * The JSON discriminator field is `"backend"`, matching the backend identifier used
 * by [KeyManager]. Example:
 * ```json
 * {"backend":"jwk","keyType":"secp256r1"}
 * {"backend":"tse","keyType":"secp256r1","config":{"server":"...","auth":{...}}}
 * ```
 */
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("backend")
@Serializable
sealed class TypedKeyGenerationRequest {
    abstract val keyType: KeyType

    /** Software JWK key. No external KMS required. */
    @SerialName("jwk")
    @Serializable
    data class Jwk(
        override val keyType: KeyType = KeyType.secp256r1,
    ) : TypedKeyGenerationRequest()

    /** HashiCorp Vault Transit Secrets Engine. */
    @SerialName("tse")
    @Serializable
    data class Tse(
        override val keyType: KeyType = KeyType.secp256r1,
        val config: TSEKeyMetadata,
    ) : TypedKeyGenerationRequest()

    /** Azure Key Vault. */
    @SerialName("azure-rest-api")
    @Serializable
    data class Azure(
        override val keyType: KeyType = KeyType.secp256r1,
        val config: AzureKeyMetadata,
    ) : TypedKeyGenerationRequest()

    /** Oracle Cloud Infrastructure (OCI) KMS. */
    @SerialName("oci-rest-api")
    @Serializable
    data class Oci(
        override val keyType: KeyType = KeyType.secp256r1,
        val config: OCIKeyMetadata,
    ) : TypedKeyGenerationRequest()

    /** AWS KMS. */
    @SerialName("aws-rest-api")
    @Serializable
    data class Aws(
        override val keyType: KeyType = KeyType.secp256r1,
        val config: AWSKeyMetadata,
    ) : TypedKeyGenerationRequest()
}
