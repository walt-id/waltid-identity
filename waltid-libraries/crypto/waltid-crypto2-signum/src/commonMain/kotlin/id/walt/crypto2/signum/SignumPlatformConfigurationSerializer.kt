package id.walt.crypto2.signum

import id.walt.crypto2.keys.PlatformKeyConfiguration
import kotlinx.serialization.json.*

/** Retains provider descriptor bytes written before the common platform model was adopted. */
internal object SignumPlatformConfigurationSerializer : JsonTransformingSerializer<PlatformKeyConfiguration>(PlatformKeyConfiguration.serializer()) {
    override fun transformSerialize(element: JsonElement): JsonElement = rename(element,
        "id.walt.crypto2.keys.PlatformKeyConfiguration.", "id.walt.crypto2.signum.SignumPlatformPolicy.")
    override fun transformDeserialize(element: JsonElement): JsonElement = rename(element,
        "id.walt.crypto2.signum.SignumPlatformPolicy.", "id.walt.crypto2.keys.PlatformKeyConfiguration.")

    private fun rename(element: JsonElement, source: String, target: String): JsonElement {
        val fields = element.jsonObject
        val type = fields["type"]?.jsonPrimitive?.content ?: return element
        return if (type.startsWith(source)) JsonObject(fields + ("type" to JsonPrimitive(target + type.removePrefix(source)))) else element
    }
}
