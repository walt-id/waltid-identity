@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.crypto2.keys

import id.walt.crypto2.serialization.BinaryData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.Transient
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class ProviderId(val value: String) {
    init {
        require(value.isNotBlank()) { "Provider ID cannot be blank" }
    }
}

@Serializable
@JvmInline
value class KeyId(val value: String) {
    init {
        require(value.isNotBlank()) { "Key ID cannot be blank" }
    }
}

@Serializable
@JsonClassDiscriminator("format")
sealed interface EncodedKey {
    val data: BinaryData
    val encodingFormat: KeyEncodingFormat

    @Serializable
    @SerialName("jwk")
    data class Jwk(
        override val data: BinaryData,
        val privateMaterial: Boolean,
    ) : EncodedKey {
        @Transient
        override val encodingFormat = KeyEncodingFormat.JWK
    }

    @Serializable
    @SerialName("spki-der")
    data class SpkiDer(override val data: BinaryData) : EncodedKey {
        @Transient
        override val encodingFormat = KeyEncodingFormat.SPKI_DER
    }

    @Serializable
    @SerialName("pkcs8-der")
    data class Pkcs8Der(override val data: BinaryData) : EncodedKey {
        @Transient
        override val encodingFormat = KeyEncodingFormat.PKCS8_DER
    }
}

enum class KeyEncodingFormat {
    JWK,
    SPKI_DER,
    PKCS8_DER,
}

@Serializable
@JsonClassDiscriminator("kind")
sealed interface StoredKey {
    val version: Int
    val id: KeyId
    val spec: KeySpec
    val usages: Set<KeyUsage>
    val metadata: Map<String, String>

    @Serializable
    @SerialName("software")
    data class Software(
        override val version: Int,
        override val id: KeyId,
        override val spec: KeySpec,
        override val usages: Set<KeyUsage>,
        val material: EncodedKey,
        override val metadata: Map<String, String> = emptyMap(),
    ) : StoredKey {
        init {
            require(version == CURRENT_VERSION) { "Unsupported software key version: $version" }
        }
    }

    @Serializable
    @SerialName("managed")
    data class Managed(
        override val version: Int,
        override val id: KeyId,
        override val spec: KeySpec,
        override val usages: Set<KeyUsage>,
        val provider: ProviderId,
        val providerSchemaVersion: Int,
        val providerData: BinaryData,
        val publicKey: EncodedKey? = null,
        override val metadata: Map<String, String> = emptyMap(),
    ) : StoredKey {
        init {
            require(version == CURRENT_VERSION) { "Unsupported managed key version: $version" }
            require(providerSchemaVersion > 0) { "Provider schema version must be positive" }
            require(publicKey !is EncodedKey.Pkcs8Der) { "Managed public key cannot contain PKCS8 private material" }
            (publicKey as? EncodedKey.Jwk)?.requirePublicJwk(spec)
        }
    }

    companion object {
        const val CURRENT_VERSION = 1
    }
}
