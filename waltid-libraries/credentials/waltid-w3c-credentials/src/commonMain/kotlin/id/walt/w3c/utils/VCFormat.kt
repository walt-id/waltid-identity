package id.walt.w3c.utils

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class VCFormat(val value: String) {
  jwt("jwt"),
  jwt_vc("jwt_vc"),
  jwt_vp("jwt_vp"),
  ldp_vc("ldp_vc"),
  ldp_vp("ldp_vp"),
  ldp("ldp"),
  jwt_vc_json("jwt_vc_json"),
  jwt_vp_json("jwt_vp_json"),
  mso_mdoc("mso_mdoc"),
  @SerialName("dc+sd-jwt")
  sd_jwt_dc("dc+sd-jwt"),
  @SerialName("vc+sd-jwt")
  sd_jwt_vc("vc+sd-jwt")
}
