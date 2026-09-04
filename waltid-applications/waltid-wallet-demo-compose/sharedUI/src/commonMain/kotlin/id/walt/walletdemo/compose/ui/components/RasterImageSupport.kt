package id.walt.walletdemo.compose.ui.components

/**
 * URL and magic-byte helpers for credential/issuer display images.
 *
 * HTTPS SVG and common raster types are allowed. HTML error pages, XML that is not SVG,
 * and PDFs are rejected so they never reach a decoder.
 */
internal object RasterImageSupport {
    private val displayExtensions = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "svgz", "avif",
    )
    private val blockedExtensions = setOf("xml", "html", "htm", "pdf")

    fun isHttpsDisplayImageUrl(value: String?): Boolean {
        val url = value?.trim()?.takeIf { it.startsWith("https://", ignoreCase = true) } ?: return false
        val extension = pathExtension(url) ?: return true
        return extension in displayExtensions
    }

    fun looksLikeRaster(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        return when {
            bytes.startsWith(0x89, 0x50, 0x4E, 0x47) -> true
            bytes.startsWith(0xFF, 0xD8, 0xFF) -> true
            bytes.startsWithAscii("GIF87a") || bytes.startsWithAscii("GIF89a") -> true
            bytes.size >= 12 && bytes.startsWithAscii("RIFF") &&
                bytes.copyOfRange(8, 12).decodeToString() == "WEBP" -> true
            bytes.startsWith(0x42, 0x4D) -> true
            bytes.size >= 12 &&
                bytes.copyOfRange(4, 8).decodeToString() == "ftyp" &&
                bytes.copyOfRange(8, 12).decodeToString().lowercase() in setOf("avif", "avis") -> true
            else -> false
        }
    }

    fun looksLikeSvg(bytes: ByteArray): Boolean {
        val start = bytes.skipLeadingWhitespace()
        if (start.startsWithAscii("<svg") || start.startsWithAscii("<SVG")) return true
        if (
            !start.startsWithAscii("<?xml") &&
            !start.startsWithAscii("<?XML") &&
            !start.startsWithAscii("<!DOCTYPE svg") &&
            !start.startsWithAscii("<!doctype svg")
        ) {
            return false
        }
        return "<svg" in start.decodeToString().lowercase()
    }

    fun looksLikeUnsupportedMarkup(bytes: ByteArray): Boolean {
        if (looksLikeSvg(bytes)) return false
        val start = bytes.skipLeadingWhitespace()
        return start.startsWithAscii("<?xml") ||
            start.startsWithAscii("<!DOCTYPE") ||
            start.startsWithAscii("<!doctype") ||
            start.startsWithAscii("<html") ||
            start.startsWithAscii("<HTML")
    }

    fun guessMimeType(bytes: ByteArray): String = when {
        looksLikeSvg(bytes) -> "image/svg+xml"
        bytes.startsWith(0x89, 0x50, 0x4E, 0x47) -> "image/png"
        bytes.startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
        bytes.startsWithAscii("GIF87a") || bytes.startsWithAscii("GIF89a") -> "image/gif"
        bytes.size >= 12 && bytes.startsWithAscii("RIFF") &&
            bytes.copyOfRange(8, 12).decodeToString() == "WEBP" -> "image/webp"
        bytes.startsWith(0x42, 0x4D) -> "image/bmp"
        bytes.size >= 12 && bytes.copyOfRange(4, 8).decodeToString() == "ftyp" -> "image/avif"
        else -> "application/octet-stream"
    }

    private fun pathExtension(url: String): String? {
        val path = url.substringBefore('#').substringBefore('?')
        val lastSegment = path.substringAfterLast('/')
        val dot = lastSegment.lastIndexOf('.')
        if (dot <= 0 || dot == lastSegment.lastIndex) return null
        val extension = lastSegment.substring(dot + 1).lowercase()
        if (extension in blockedExtensions) return extension
        return extension.takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
    }

    private fun ByteArray.skipLeadingWhitespace(): ByteArray {
        var index = 0
        while (index < size && this[index].toInt() and 0xFF <= 0x20) index++
        return if (index == 0) this else copyOfRange(index, size)
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { this[it].toInt() and 0xFF == prefix[it] }

    private fun ByteArray.startsWithAscii(prefix: String): Boolean =
        size >= prefix.length && prefix.indices.all { this[it].toInt().toChar() == prefix[it] }
}
