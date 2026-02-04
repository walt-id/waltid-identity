package id.walt.policies2.vc.policies.status.reader.format

interface FormatMatcher {
    fun matches(input: String): Boolean
}