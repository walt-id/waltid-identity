package id.walt.crypto.keys.tse

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class TSEKeyMetadata(
    val server: String,
    val auth: TSEAuth,
    val namespace: String? = null,
    val id: String? = null,
) {
    @JsName("TSEKeyMetadata2")
    constructor(
        server: String,
        token: String,
        namespace: String? = null,
        id: String? = null,
    ) : this(server, TSEAuth(accessKey = token), namespace, id)
}
