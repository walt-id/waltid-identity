package id.walt.cli.exceptions

import java.text.ParseException

actual class InvalidJWKFormat(val msg: String, val pos: Int) : ParseException(msg, pos) {
}