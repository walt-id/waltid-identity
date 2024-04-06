package id.walt.did.dids.registrar.local.cheqd.models.job.didstates

//import com.beust.klaxon.Json
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class VerificationMethod(
    val controller: String,
    val id: String,
    //@Json(serializeNull = false)
    val publicKeyMultibase: String? = null,
    //@Json(serializeNull = false)
    val publicKeyBase58: String? = null,
    val type: String
)
