package id.walt.cli.util

import java.io.FileNotFoundException
import java.net.URI

fun getResourcePath(obj: Any, filename: String): String {
    // The returned URL has white spaces replaced by %20.
    // So, we need to decode it first to get rid of %20 from the file path
    obj.javaClass.getClassLoader().getResource(filename)?.let {
        return URI(it.toString()).path
    }

    throw FileNotFoundException(filename)
}