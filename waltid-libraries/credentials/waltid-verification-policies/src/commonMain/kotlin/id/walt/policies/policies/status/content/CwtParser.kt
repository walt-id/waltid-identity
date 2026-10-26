package id.walt.policies.policies.status.content

import id.walt.cose.CoseSign1

/**
 * Parses an IETF Token Status List CWT from a hex-encoded tagged COSE_Sign1.
 * [id.walt.policies.policies.status.CredentialFetcher] converts binary
 * `application/statuslist+cwt` bodies to hex before this parser runs.
 */
class CwtParser : ContentParser<String, ByteArray> {
    override fun parse(response: String): ByteArray {
        val payload = CoseSign1.fromTagged(response.trim()).payload
        return requireNotNull(payload) { "Expecting non-empty payload." }
    }
}