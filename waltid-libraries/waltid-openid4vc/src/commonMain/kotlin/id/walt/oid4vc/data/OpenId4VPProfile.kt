package id.walt.oid4vc.data

enum class OpenId4VPProfile {
  DEFAULT,
  ISO_18013_7_MDOC,
  EBSIV3,
  HAIP;

  companion object {
    fun fromAuthorizeBaseURL(baseURL: String?): OpenId4VPProfile {
      return if(baseURL?.lowercase()?.startsWith("mdoc-openid4vp://") == true)
        ISO_18013_7_MDOC
      else if(baseURL?.lowercase()?.startsWith("haip://") == true)
        HAIP
      else DEFAULT
    }
  }
}
