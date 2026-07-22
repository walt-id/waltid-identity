package id.walt.crypto2.keys

import id.walt.crypto2.serialization.BinaryData
import kotlin.io.encoding.Base64

fun EncodedKey.SpkiDer.encodePem(): String = encodePem(PUBLIC_KEY_LABEL)

fun EncodedKey.Pkcs8Der.encodePem(): String = encodePem(PRIVATE_KEY_LABEL)

fun String.decodePublicKeyPem(): EncodedKey.SpkiDer =
    EncodedKey.SpkiDer(BinaryData(decodePem(PUBLIC_KEY_LABEL)))

fun String.decodePrivateKeyPem(): EncodedKey.Pkcs8Der =
    EncodedKey.Pkcs8Der(BinaryData(decodePem(PRIVATE_KEY_LABEL)))

private fun EncodedKey.encodePem(label: String): String {
    val encoded = Base64.Default.encode(data.toByteArray()).chunked(PEM_LINE_LENGTH).joinToString("\n")
    return "-----BEGIN $label-----\n$encoded\n-----END $label-----\n"
}

private fun String.decodePem(expectedLabel: String): ByteArray {
    require(isNotEmpty()) { "PEM cannot be empty" }
    require(all { it.code <= ASCII_MAX }) { "PEM must contain ASCII characters only" }

    val normalized = when {
        "\r\n" in this -> {
            require(replace("\r\n", "").none { it == '\r' || it == '\n' }) {
                "PEM must not mix line endings"
            }
            replace("\r\n", "\n")
        }
        else -> {
            require('\r' !in this) { "PEM contains an invalid line ending" }
            this
        }
    }.removeSuffix("\n")

    val lines = normalized.split('\n')
    require(lines.size >= 3 && lines.none(String::isEmpty)) { "PEM must contain one non-empty document" }
    val label = lines.first().pemBeginLabel()
    require(label in SUPPORTED_LABELS) { "Unsupported PEM label: $label" }
    require(label == expectedLabel) { "Expected $expectedLabel PEM, found $label" }
    require(lines.last() == "-----END $label-----") { "PEM END label does not match BEGIN label" }

    val body = lines.subList(1, lines.lastIndex)
    require(body.dropLast(1).all { it.length == PEM_LINE_LENGTH }) {
        "PEM base64 lines must be 64 characters"
    }
    require(body.last().length in 4..PEM_LINE_LENGTH && body.last().length % 4 == 0) {
        "PEM final base64 line has an invalid length"
    }
    require(body.all { line -> line.all { it.isBase64Character() } }) { "PEM contains invalid base64" }

    val encoded = body.joinToString("")
    val decoded = try {
        Base64.Default.decode(encoded)
    } catch (cause: IllegalArgumentException) {
        throw IllegalArgumentException("PEM contains invalid base64", cause)
    }
    require(decoded.isNotEmpty()) { "PEM key material cannot be empty" }
    require(Base64.Default.encode(decoded) == encoded) { "PEM base64 must be canonical" }
    return decoded
}

private fun String.pemBeginLabel(): String {
    require(startsWith(BEGIN_PREFIX) && endsWith(BOUNDARY_SUFFIX)) { "PEM has an invalid BEGIN label" }
    return substring(BEGIN_PREFIX.length, length - BOUNDARY_SUFFIX.length).also {
        require(it.isNotEmpty()) { "PEM label cannot be empty" }
    }
}

private fun Char.isBase64Character(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '+' || this == '/' || this == '='

private const val PUBLIC_KEY_LABEL = "PUBLIC KEY"
private const val PRIVATE_KEY_LABEL = "PRIVATE KEY"
private const val BEGIN_PREFIX = "-----BEGIN "
private const val BOUNDARY_SUFFIX = "-----"
private const val PEM_LINE_LENGTH = 64
private const val ASCII_MAX = 0x7f
private val SUPPORTED_LABELS = setOf(PUBLIC_KEY_LABEL, PRIVATE_KEY_LABEL)
