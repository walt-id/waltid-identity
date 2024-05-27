package id.walt.oid4vc.data

enum class OpenId4VPProfile {
  Default,
  ISO_18013_7_MDOC;

  companion object {
    fun fromAuthorizeBaseURL(baseURL: String?): OpenId4VPProfile? {
      return if(baseURL?.lowercase()?.startsWith("mdoc-openid4vp://") == true)
        ISO_18013_7_MDOC
      else null
    }
  }
}
