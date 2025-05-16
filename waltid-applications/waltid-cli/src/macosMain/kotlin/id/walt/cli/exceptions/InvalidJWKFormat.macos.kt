package id.walt.cli.exceptions

actual class InvalidJWKFormat(
    val msg: String,
    val pos: Int
) : Exception("$msg (at position $pos)")