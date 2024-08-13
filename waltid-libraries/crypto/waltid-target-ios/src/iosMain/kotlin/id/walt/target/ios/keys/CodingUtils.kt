package id.walt.target.ios.keys

import kotlin.io.encoding.Base64

internal val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)