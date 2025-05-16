package id.walt.cli.io

actual class Files {
    actual companion object {
        actual fun exists(it: Path): Boolean =
            it.exists()

        actual fun isRegularFile(it: Path): Boolean =
            it.toFile().isFile()

        actual fun isDirectory(it: Path): Boolean =
            it.toFile().isDirectory()

        actual fun isWritable(it: Path): Boolean =
            it.toFile().canWrite()

        actual fun isReadable(it: Path): Boolean =
            it.toFile().canRead()

        actual fun isSymbolicLink(it: Path): Boolean =
            java.nio.file.Files.isSymbolicLink(it.innerPath)
    }

}