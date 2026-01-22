package id.walt.policies2.vc.policies.status.reader.format

class CwtFormatMatcher : FormatMatcher {
    override fun matches(input: String) = input.trim().matches(Regex("^[0-9a-fA-F]+$"))
}