package id.walt.cli.io

actual class Files {
    actual companion object {
        actual fun exists(it: id.walt.cli.io.Path): Boolean =
            it.exists()

        actual fun isRegularFile(it: id.walt.cli.io.Path): Boolean =
            it.toFile().isFile()

        actual fun isDirectory(it: id.walt.cli.io.Path): Boolean =
            it.toFile().isDirectory()

        actual fun isWritable(it: id.walt.cli.io.Path): Boolean =
            it.toFile().canWrite()

        actual fun isReadable(it: id.walt.cli.io.Path): Boolean =
            it.toFile().canRead()

        actual fun isSymbolicLink(it: id.walt.cli.io.Path): Boolean =
            java.nio.file.Files.isSymbolicLink(it.innerPath)
    }

}