package id.walt.policies

import id.walt.credentials.utils.VCFormat
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@Serializable
@OptIn(ExperimentalJsExport::class, ExperimentalSerializationApi::class)
@JsExport
@JsonClassDiscriminator("type")
abstract class VerificationPolicy {
    abstract val name: String
    abstract val description: String
    abstract val supportedVCFormats: Set<VCFormat>
}
