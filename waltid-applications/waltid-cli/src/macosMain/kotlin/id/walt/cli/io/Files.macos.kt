package id.walt.cli.io

import kotlinx.cinterop.*
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeSymbolicLink
import platform.posix.close
import platform.posix.mkstemps
import platform.posix.perror

//import kotlin.io.path.Path

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

        actual fun createTempFile(fileName: String, extension: String): File {
            val tempDir = "/tmp"
            val template = "$tempDir/$fileName-XXXXXX.$extension"

            // Allocate a mutable copy of the template
            val templateBytes = template.encodeToByteArray() + 0 // null-terminated

            return memScoped {
                // Allocate space for the template string
                val cTemplate = allocArray<ByteVar>(templateBytes.size)
                templateBytes.forEachIndexed { index, byte ->
                    cTemplate[index] = byte.toByte()
                }

                val fd = mkstemps(cTemplate, extension.length + 1) // +1 for the dot
                if (fd == -1) {
                    perror("mkstemps")
                    throw RuntimeException("Failed to create temp file")
                }

                val path = cTemplate.toKString()
                close(fd) // Optionally close file descriptor

                File(path) // This assumes you're using your own expect/actual File abstraction
            }


//            val cTemplate = templateBytes.toCValues().copyOf()
//
//            val fd = mkstemps(cTemplate.refTo(0), extension.length + 1) // +1 for the dot
//            if (fd == -1) {
//                perror("mkstemps")
//                throw RuntimeException("Failed to create temp file")
//            }
//
//            val path = cTemplate.toKString()
//            close(fd) // optional: depends on whether you want to keep the file open
//
//            return File(path)

        }
    }
}