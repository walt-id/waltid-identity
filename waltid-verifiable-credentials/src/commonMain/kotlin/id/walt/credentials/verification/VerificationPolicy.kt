package id.walt.credentials.verification

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
@Serializable
abstract class VerificationPolicy(
    open val name: String,
    @Transient open val description: String? = null
)
