package id.walt.iso18013.annexc.cbor

import java.util.Base64

object Base64UrlNoPad {
    fun encode(data: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    fun decode(text: String): ByteArray = Base64.getUrlDecoder().decode(text)
}
