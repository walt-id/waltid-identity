package id.walt.cli.io

actual class FileSystem actual constructor(innerFs: Any) {
//    actual fun createFile(path: String): File {
//        return File(path)
//    }

    actual fun getPath(path: String): Path {
        return Path(path)
    }

//    actual fun readResourceAsText(path: String): String {
//        return File(path).readText()
//    }
}