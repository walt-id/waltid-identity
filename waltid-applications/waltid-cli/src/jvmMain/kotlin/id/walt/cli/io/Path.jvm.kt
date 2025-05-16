package id.walt.cli.io

import java.nio.file.Paths
import kotlin.io.path.exists

actual class Path actual constructor(val path: String) {

    val innerPath = Paths.get(path)

    actual fun toFile(): File = File(innerPath.toAbsolutePath().toString())
    actual fun exists(): Boolean = innerPath.exists()
    actual fun toAbsolutePath(): String = innerPath.toAbsolutePath().toString()
}