package id.walt.did.dids.registrar.dids

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class DidEbsiCreateOptions(version: Int, token: String) : DidCreateOptions(
    method = "ebsi",
    config = config(config = mapOf("version" to version), secret = mapOf("token" to token))
)
