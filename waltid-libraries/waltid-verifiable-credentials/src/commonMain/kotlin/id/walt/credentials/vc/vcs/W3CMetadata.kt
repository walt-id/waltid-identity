package id.walt.credentials.vc.vcs

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
interface W3CMetadata {
    val defaultContext: List<String>
}
