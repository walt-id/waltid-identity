package id.walt.cli.io

actual class FileSystem(val innerFS: java.nio.file.FileSystem) {
    actual fun createFile(path: String): File = File(path)
    actual fun getPath(path: String): Path = Path(path)
    actual fun readResourceAsText(path: String): String = TODO("Implement using platform-specific resource loading")
}