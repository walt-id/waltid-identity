package id.walt.policies.policies.status.reader.format

class CwtFormatMatcher : FormatMatcher {
    companion object {
        private val hexRegex = Regex("^[0-9a-fA-F]+$")
        private const val MIN_HEX_LENGTH = 100
    }

    override fun matches(input: String): Boolean = with(input.trim()) {
        length >= MIN_HEX_LENGTH && matches(hexRegex)
    }
}