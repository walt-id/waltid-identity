package id.walt.json

import kotlinx.cinterop.ExperimentalForeignApi
import platform.JavaScriptCore.JSContext
import platform.JavaScriptCore.JSValue

@OptIn(ExperimentalForeignApi::class)
internal actual fun jsonNumberFormatter(): (Double) -> String {
    val context = JSContext()
    val stringify = requireNotNull(context.evaluateScript("(function(value) { return String(value); })"))
    return { number ->
        // The common caller accepts finite Double values only, never arbitrary JavaScript source.
        requireNotNull(stringify.callWithArguments(listOf(JSValue.valueWithDouble(number, inContext = context)))) { "Cannot serialize JSON number" }.toString()
    }
}
