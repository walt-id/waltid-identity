package id.walt.did.dids.registrar.dids

import kotlinx.serialization.json.JsonElement
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class DidEbsiCreateOptions(version: Int, verifiableAuthorisationToOnboard: JsonElement) : DidCreateOptions(
    method = "ebsi",
    config = config(config = mapOf("version" to version), secret = mapOf("verifiableAuthorisationToOnboard" to verifiableAuthorisationToOnboard))
)
