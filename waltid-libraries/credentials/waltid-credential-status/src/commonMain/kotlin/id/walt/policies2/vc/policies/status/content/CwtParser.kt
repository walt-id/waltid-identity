package id.walt.policies2.vc.policies.status.content

import id.walt.cose.CoseSign1

/**
 * Parser for CWT (CBOR Web Token) status list content.
 * Accepts binary CBOR data and extracts the payload.
 */
class CwtParser : ContentParser<ByteArray, ByteArray> {
    override fun parse(response: ByteArray): ByteArray {
        val payload = CoseSign1.fromTagged(response).payload
        return requireNotNull(payload) { "Expecting non-empty payload." }
    }
}