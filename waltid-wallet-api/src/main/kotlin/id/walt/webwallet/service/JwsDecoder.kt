package id.walt.webwallet.service

import id.walt.crypto.utils.JwsUtils.decodeJws

class JwsDecoder {
    fun payload(data: String) = data.decodeJws(allowMissingSignature = true).payload
}