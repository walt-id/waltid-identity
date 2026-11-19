package id.walt.ades.x509

import kotlin.io.encoding.Base64

object Pem {
    private val block = Regex("-----BEGIN ([A-Z0-9 ]+)-----([\\s\\S]*?)-----END \\1-----")

    /** All PEM blocks with the given label (e.g. "CERTIFICATE"); returns DER bytes. */
    fun decodeAll(text: String, label: String? = null): List<ByteArray> = block.findAll(text)
        .filter { label == null || it.groupValues[1] == label }
        .map { Base64.Mime.decode(it.groupValues[2].replace(Regex("\\s"), "")) }
        .toList()

    fun encode(label: String, der: ByteArray): String =
        "-----BEGIN $label-----\n" + Base64.encode(der).chunked(64).joinToString("\n") + "\n-----END $label-----\n"

    /** Accepts PEM or DER certificate input. */
    fun certificates(data: ByteArray): List<X509Certificate> {
        val text = runCatching { data.decodeToString() }.getOrNull()
        return if (text != null && text.contains("-----BEGIN CERTIFICATE-----")) {
            decodeAll(text, "CERTIFICATE").map(::X509Certificate)
        } else listOf(X509Certificate(data))
    }
}

internal fun ByteArray.hex(): String = toHexString()
