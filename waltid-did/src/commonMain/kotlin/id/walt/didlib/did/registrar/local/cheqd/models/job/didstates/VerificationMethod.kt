package id.walt.didlib.did.registrar.local.cheqd.models.job.didstates

//import com.beust.klaxon.Json
import kotlinx.serialization.Serializable

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
