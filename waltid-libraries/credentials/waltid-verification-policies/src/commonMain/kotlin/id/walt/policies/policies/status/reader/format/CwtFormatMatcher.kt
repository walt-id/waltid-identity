package id.walt.policies.policies.status.reader.format

/**
 * Matches IETF Token Status List CWT content after [id.walt.policies.policies.status.CredentialFetcher]
 * hex-encodes binary COSE_Sign1 bytes (or when a server already returns hex).
 */
class CwtFormatMatcher : FormatMatcher {
    companion object {
        private val hexRegex = Regex("^[0-9a-fA-F]+$")
        private const val MIN_HEX_LENGTH = 100
    }

    override fun matches(input: String): Boolean = with(input.trim()) {
        length >= MIN_HEX_LENGTH && matches(hexRegex)
    }
}