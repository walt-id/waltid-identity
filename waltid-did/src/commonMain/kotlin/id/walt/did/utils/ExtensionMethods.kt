package id.walt.did.utils

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
object ExtensionMethods {
    fun String.ensurePrefix(prefix: String) = this.takeIf { it.startsWith(prefix) } ?: prefix.plus(this)
}
