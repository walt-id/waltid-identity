package id.walt.definitionparser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PresentationSubmission(
    val id: String,

    @SerialName("definition_id")
    val definitionId: String,

    @SerialName("descriptor_map")
    val descriptorMap: List<Descriptor>
) {
    @Serializable
    data class Descriptor(
        val id: String? = null,
        val format: JsonElement,
        val path: String,

        @SerialName("path_nested")
        val pathNested: Descriptor? = null
    )
}

