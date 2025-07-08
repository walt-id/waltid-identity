package id.walt.policies.policies

import java.util.*

object Base64Utils {
    fun decode(base64: String): ByteArray = Base64.getDecoder().decode(base64)
}