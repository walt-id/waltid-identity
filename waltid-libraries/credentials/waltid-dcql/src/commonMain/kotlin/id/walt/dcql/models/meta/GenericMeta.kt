package id.walt.dcql.models.meta

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A generic meta object for formats not explicitly typed or for extensions.
 * Use this sparingly, prefer specific types.
 */
@Serializable
@SerialName("GenericMeta")
data class GenericMeta(
    val properties: Map<String, JsonElement> // Allows arbitrary key-value pairs
) : CredentialQueryMeta {
    override val format = null
    override fun getTypeString() = null
}

// Add other meta types for other formats like AnonCreds if/when supported
// e.g., AnonCredsMeta with schema_id_values, cred_def_id_values

