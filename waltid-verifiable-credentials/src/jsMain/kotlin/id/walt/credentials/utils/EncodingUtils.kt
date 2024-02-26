package id.walt.credentials.utils
@OptIn(ExperimentalJsExport::class)
@JsExport
actual object EncodingUtils {
    actual fun urlEncode(path: String): String = js("encodeURIComponent")(path)

    actual fun urlDecode(path: String): String = js("decodeURIComponent")(path)
}
