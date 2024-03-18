package id.walt.oid4vc.data

enum class AccessTokenScope(val value: String) {
  OpenId("openid");

  companion object {
    fun fromValue(value: String): AccessTokenScope? {
      return entries.find { it.value == value }
    }
  }
}