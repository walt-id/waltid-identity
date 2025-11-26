package id.walt.commons

import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths

fun fetchBinaryFile(vicalUrl: String): ByteArray? {
    return try {
        val uri = URI(vicalUrl)
        when (val scheme = uri.scheme?.lowercase()) {
            "file" -> Files.readAllBytes(Paths.get(uri))
            "http", "https" -> uri.toURL().openStream().use { it.readBytes() }
            else -> error("Unsupported URL scheme: $scheme")
        }
    } catch (e: Exception) {
        throw IllegalArgumentException("Failed to fetch binary file from $vicalUrl")
    }
}