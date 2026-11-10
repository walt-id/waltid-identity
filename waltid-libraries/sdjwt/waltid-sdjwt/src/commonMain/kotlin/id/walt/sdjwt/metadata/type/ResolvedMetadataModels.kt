package id.walt.sdjwt.metadata.type

import kotlinx.serialization.json.*

/** Claim path components preserve the difference between object keys, array indices and wildcards. */
sealed interface MetadataClaimPathComponent {
    data class Name(val value: String) : MetadataClaimPathComponent
    data class Index(val value: Int) : MetadataClaimPathComponent {
        init { require(value >= 0) }
    }
    data object AnyIndex : MetadataClaimPathComponent
}

/** Normalized, validated display entry. Original extension properties remain available. */
class ResolvedMetadataDisplay internal constructor(val json: JsonObject) {
    val locale: String = json.getValue("locale").jsonPrimitive.content
    val name: String = json.getValue("name").jsonPrimitive.content
    val description: String? = (json["description"] as? JsonPrimitive)?.contentOrNull
    val rendering: JsonObject? = json["rendering"] as? JsonObject
}

class ResolvedMetadataClaimDisplay internal constructor(val json: JsonObject) {
    val locale: String = json.getValue("locale").jsonPrimitive.content
    val label: String = json.getValue("label").jsonPrimitive.content
}

/** Omitted restrictions remain null, so defaults cannot accidentally weaken an inherited constraint. */
class ResolvedMetadataClaim internal constructor(val json: JsonObject) {
    val path: List<MetadataClaimPathComponent> = json.getValue("path").jsonArray.map { value ->
        when {
            value == JsonNull -> MetadataClaimPathComponent.AnyIndex
            value.jsonPrimitive.isString -> MetadataClaimPathComponent.Name(value.jsonPrimitive.content)
            else -> MetadataClaimPathComponent.Index(value.jsonPrimitive.int)
        }
    }
    val mandatory: Boolean? = (json["mandatory"] as? JsonPrimitive)?.booleanOrNull
    val sd: ClaimSdMetadata? = (json["sd"] as? JsonPrimitive)?.contentOrNull?.let { value ->
        when (value) { "always" -> ClaimSdMetadata.Always; "never" -> ClaimSdMetadata.Never; else -> ClaimSdMetadata.Allowed }
    }
    val display: List<ResolvedMetadataClaimDisplay> = (json["display"] as? JsonArray)
        ?.map { ResolvedMetadataClaimDisplay(it.jsonObject) }.orEmpty()
}
