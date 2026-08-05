package id.walt.crypto.utils

fun requireHttpEndpoint(value: String, name: String) {
    require(value.startsWith("http://") || value.startsWith("https://")) { "$name must use HTTP or HTTPS" }
    val authority = value.substringAfter("://").takeWhile { it != '/' && it != '?' && it != '#' }
    require(authority.isNotBlank() && '@' !in authority) { "$name must include a host without user information" }
    require('?' !in value && '#' !in value) { "$name must not contain a query or fragment" }
}

fun requireEndpointHost(value: String, name: String) {
    require(value.isNotBlank() && value.none(Char::isWhitespace)) { "$name must not be blank or contain whitespace" }
    require("://" !in value && '/' !in value && '?' !in value && '#' !in value && '@' !in value) {
        "$name must be a host name"
    }
}
