package id.walt.policies.policies.status.content

import id.walt.cose.CoseSign1

class CwtParser : ContentParser<String, ByteArray> {
    override fun parse(response: String): ByteArray {
        val payload = CoseSign1.fromTagged(response).payload
        return requireNotNull(payload) { "Expecting non-empty payload." }
    }
}