package id.walt.cli.io

import java.nio.file.FileSystems

actual class FileSystems {
    actual companion object {
        actual fun getDefault(): FileSystem {
            return id.walt.cli.io.FileSystem(FileSystems.getDefault())
        }
    }
}