package id.walt.oid4vc.data

import kotlinx.serialization.json.*

data class VpTokenParameter constructor(
  val vpTokenStrings: Set<String>,
  val vpTokenObjects: List<JsonObject>
) {
  constructor(vpTokenStrings: Set<String>): this(vpTokenStrings, listOf())
  constructor(vpToken: String): this(setOf(vpToken), listOf())
  constructor(vpTokenObjects: List<JsonObject>): this(setOf(), vpTokenObjects)
  constructor(vpToken: JsonObject): this(setOf(), listOf(vpToken))
  fun toJsonElement(): JsonElement {
    return when {
      vpTokenStrings.size == 1 && vpTokenObjects.isEmpty() -> JsonUnquotedLiteral(vpTokenStrings.first())
      vpTokenObjects.size == 1 && vpTokenStrings.isEmpty() -> vpTokenObjects.first()
      else -> JsonArray(vpTokenStrings.map { JsonPrimitive(it) }.plus(vpTokenObjects))
      }
  }

  companion object {
    fun fromJsonElement(element: JsonElement): VpTokenParameter {
      return when(element) {
        is JsonObject -> VpTokenParameter(element)
        is JsonPrimitive -> VpTokenParameter(element.content)
        is JsonArray -> VpTokenParameter(
          element.filterIsInstance<JsonPrimitive>().map { it.content }.toSet(),
          element.filterIsInstance<JsonObject>()
        )
      }
    }
  }
}
