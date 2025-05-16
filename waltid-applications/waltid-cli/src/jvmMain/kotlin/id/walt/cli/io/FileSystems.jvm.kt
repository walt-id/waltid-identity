package id.walt.cli.io

actual class FileSystems {
    actual companion object {
        actual fun getDefault(): FileSystem {
            return FileSystem(java.nio.file.FileSystems.getDefault())
        }
    }
}