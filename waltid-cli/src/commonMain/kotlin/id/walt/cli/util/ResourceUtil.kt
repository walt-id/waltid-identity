package id.walt.cli.util

import java.io.FileNotFoundException
import java.net.URI
import java.nio.file.Paths
import kotlin.io.path.exists

fun getResourcePath(obj: Any, filename: String): String {
    // The returned URL has white spaces replaced by %20.
    // So, we need to decode it first to get rid of %20 from the file path
    val path = obj.javaClass.getClassLoader().getResource(filename)?.let {
        URI(it.toString()).path
    }

    // If Windows path, escape backslash
    val winPathRegex = """"^[a-zA-Z]:\\\\.*"""".toRegex()
    if (winPathRegex.matches(path!!)) {
        return path.replace("\\", "\\\\")
    }

    if (Paths.get(path).exists()) {
        return path
    } else {
        throw FileNotFoundException(path)
    }
}