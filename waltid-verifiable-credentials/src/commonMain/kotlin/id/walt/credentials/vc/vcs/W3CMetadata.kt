package id.walt.credentials.vc.vcs

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
interface W3CMetadata {
    val defaultContext: List<String>
}
