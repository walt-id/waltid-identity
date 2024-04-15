package id.walt.webwallet.service

import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.webwallet.utils.JsonUtils
import kotlinx.serialization.json.jsonObject

class JwsDecoder {
    fun payload(data: String) = data.decodeJws(allowMissingSignature = true).payload.let {
        JsonUtils.tryGetData(it, "vc")?.jsonObject ?: it
    }
}