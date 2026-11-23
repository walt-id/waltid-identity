package id.walt.json

import kotlinx.cinterop.ExperimentalForeignApi
import platform.JavaScriptCore.JSContext

@OptIn(ExperimentalForeignApi::class)
internal actual fun jsonNumberFormatter(): (Double) -> String {
    val context = JSContext()
    return { number ->
        // The common caller accepts finite Double values only, never arbitrary JavaScript source.
        requireNotNull(context.evaluateScript("String($number)")) { "Cannot serialize JSON number" }.toString()
    }
}
