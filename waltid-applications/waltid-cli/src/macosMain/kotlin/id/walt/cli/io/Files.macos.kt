package id.walt.cli.io

import kotlinx.cinterop.*
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeSymbolicLink

actual class Files {
    @OptIn(ExperimentalForeignApi::class)
    actual companion object {
        actual fun exists(it: Path): Boolean =
            NSFileManager.defaultManager.fileExistsAtPath(it.toAbsolutePath())

        actual fun isRegularFile(it: Path): Boolean {
            memScoped {
                val isDir = alloc<BooleanVar>()
                val exists = NSFileManager.defaultManager.fileExistsAtPath(it.toAbsolutePath(), isDir.ptr)
                return exists && !isDir.value
            }
        }

        actual fun isDirectory(it: Path): Boolean {
            memScoped {
                val isDir = alloc<BooleanVar>()
                val exists = NSFileManager.defaultManager.fileExistsAtPath(it.toAbsolutePath(), isDir.ptr)
                return exists && isDir.value
            }
        }

        actual fun isWritable(it: Path): Boolean =
            NSFileManager.defaultManager.isWritableFileAtPath(it.toAbsolutePath())

        actual fun isReadable(it: Path): Boolean =
            NSFileManager.defaultManager.isReadableFileAtPath(it.toAbsolutePath())

        actual fun isSymbolicLink(it: Path): Boolean {
            val fileAttributes = NSFileManager.defaultManager.attributesOfItemAtPath(it.toAbsolutePath(), null)
            val fileType = fileAttributes?.get(NSFileType) as? String
            return fileType == NSFileTypeSymbolicLink
        }
    }
}