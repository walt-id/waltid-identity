package id.walt.policies2.vc.policies.status.reader.format

import id.walt.policies2.vc.policies.status.StatusListContent

/**
 * Matcher for CWT (CBOR Web Token) status list format.
 * Matches binary content that starts with CBOR tag 18 (0xD2) for COSE_Sign1
 * or an untagged CBOR array of 4 elements (0x84).
 */
class CwtFormatMatcher : FormatMatcher {
    companion object {
        private const val COSE_SIGN1_TAG: Byte = 0xD2.toByte()
        private const val CBOR_ARRAY_4: Byte = 0x84.toByte()
    }

    override fun matches(content: StatusListContent): Boolean = when (content) {
        is StatusListContent.Binary -> {
            content.content.isNotEmpty() && 
                (content.content[0] == COSE_SIGN1_TAG || content.content[0] == CBOR_ARRAY_4)
        }
        is StatusListContent.Text -> false
    }
}