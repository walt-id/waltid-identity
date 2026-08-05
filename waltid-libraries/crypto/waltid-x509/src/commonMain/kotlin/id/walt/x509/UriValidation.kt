package id.walt.x509

private val uriScheme = Regex("[A-Za-z][A-Za-z0-9+.-]*")

internal fun requireAbsoluteUri(value: String, name: String) {
    require(value.none(Char::isWhitespace)) { "$name must not contain whitespace" }
    val separator = value.indexOf(':')
    require(separator > 0 && uriScheme.matches(value.substring(0, separator))) {
        "$name must be an absolute URI"
    }
    if (value.startsWith("http://") || value.startsWith("https://")) {
        requireHttpUrl(value, name)
    }
}

internal fun requireHttpUrl(value: String, name: String) {
    require(value.startsWith("http://") || value.startsWith("https://")) { "$name must use HTTP or HTTPS" }
    val authority = value.substringAfter("://").takeWhile { it != '/' && it != '?' && it != '#' }
    require(authority.isNotBlank() && '@' !in authority) { "$name must include a host without user information" }
}
