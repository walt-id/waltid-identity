package id.walt.oid4vc.data

enum class OpenId4VPProfile {
  Default,
  ISO_18013_7_MDOC,
  HAIP,
  EBSIV3;

  companion object {
    fun fromAuthorizeBaseURL(baseURL: String?): OpenId4VPProfile {
      return if(baseURL?.lowercase()?.startsWith("mdoc-openid4vp://") == true)
        ISO_18013_7_MDOC
      else if(baseURL?.lowercase()?.startsWith("haip://") == true)
        HAIP
      else if(baseURL?.lowercase()?.startsWith("ebsi://") == true)
        EBSIV3
      else Default
    }
  }
}
