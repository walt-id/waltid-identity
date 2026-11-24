package id.walt.mdoc.encoding

/** ISO 18013-5 version negotiation permits compatible minor versions within major version 1. */
fun requireSupportedMdocVersion(version: String) {
    require(Regex("1\\.[0-9]+").matches(version)) { "Unsupported mdoc version: $version" }
}
