package id.walt.policies.policies.status.reader.format

class JwtFormatMatcher : FormatMatcher {
    override fun matches(input: String) = input.isJwt()

    // copied over from waltid-digital-credentials/utils/JwtUtils
    // in order to avoid adding it as a dependency
    private fun String.isJwt(): Boolean = startsWith("ey") && count { it == '.' } == 2
}