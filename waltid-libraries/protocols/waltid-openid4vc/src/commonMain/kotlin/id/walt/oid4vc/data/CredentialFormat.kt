package id.walt.oid4vc.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(CredentialFormatSerializer::class)
enum class CredentialFormat(val value: String) {
    jwt_vc_json("jwt_vc_json"),
    jwt_vc_json_ld("jwt_vc_json-ld"),
    ldp_vc("ldp_vc"),
    sd_jwt_dc("dc+sd-jwt"),
    sd_jwt_vc("vc+sd-jwt"),
    mso_mdoc("mso_mdoc"),
    jwt_vp_json("jwt_vp_json"),
    jwt_vp_json_ld("jwt_vp_json-ld"),
    ldp_vp("ldp_vp"),
    jwt_vc("jwt_vc"),
    jwt_vp("jwt_vp");

    companion object {
        fun fromValue(value: String): CredentialFormat? {
            return when (value) {
                // TODO: Workaround for Ktor's parseQueryString interpreting '+' as space
                "vc+sd-jwt", "vc sd-jwt" -> sd_jwt_vc
                else -> entries.find { it.value == value }
            }
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializer(CredentialFormat::class)
object CredentialFormatSerializer : KSerializer<CredentialFormat> {
    override fun serialize(encoder: Encoder, value: CredentialFormat) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): CredentialFormat {
        val value = decoder.decodeString()
        val format = CredentialFormat.fromValue(value)
        return format ?: throw IllegalArgumentException("Unsupported format: $value")
    }
}
