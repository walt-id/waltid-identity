package id.walt.cli.io

actual class FileSystem actual constructor(innerFs: Any) {
    actual fun getPath(path: String): Path = Path(path)
}