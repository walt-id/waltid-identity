package id.walt.policies.policies.status.reader.format

interface FormatMatcher {
    fun matches(input: String): Boolean
}