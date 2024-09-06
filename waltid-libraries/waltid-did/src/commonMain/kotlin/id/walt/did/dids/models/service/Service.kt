package id.walt.did.dids.models.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class Service(
    val id: String,
    val type: String,
    val serviceEndpoint: JsonElement,
    val customParams: Map<String, JsonElement>?,
)
