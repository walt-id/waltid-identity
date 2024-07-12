package id.walt.credentials.verification

import kotlinx.serialization.*
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@Serializable
@OptIn(ExperimentalJsExport::class, ExperimentalSerializationApi::class)
@JsExport
@JsonClassDiscriminator("type")
abstract class VerificationPolicy(
    open val name: String,
    /*@Transient*/ open val description: String? = null,
)
