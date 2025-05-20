@file:OptIn(ExperimentalForeignApi::class)

package id.walt.cli.io

import kotlinx.cinterop.*
import kotlinx.io.IOException
import platform.Foundation.*

actual class File actual constructor(private val path: String) {

    private val nsPath: NSString = path as NSString
    private val fileManager = NSFileManager.defaultManager

    actual val absolutePath: String
        get() = nsPath.stringByStandardizingPath

    actual val parent: File?
        get() {
            val parentPath = nsPath.stringByDeletingLastPathComponent
            return if (parentPath.isNullOrBlank()) null else File(parentPath)
        }

    actual val name: String
        get() = nsPath.lastPathComponent

    actual val nameWithoutExtension: String
        get() = nsPath.stringByDeletingPathExtension

    actual val extension: String
        get() = nsPath.pathExtension

    actual fun exists(): Boolean =
        fileManager.fileExistsAtPath(path)

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    actual fun writeText(text: String) {
        val string = NSString.create(string = text)
        val success = string.writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        if (!success) throw IOException("Failed to write to file at $path")
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun readText(): String {
        val content = NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)
        return content?.toString() ?: throw IOException("Cannot read file at path: $path")
    }

    actual fun absolutePathString(): String = absolutePath

    actual fun toPath(): Path {
        return Path(this.absolutePath)
    }

    actual constructor(parent: File, child: String) : this("${parent.absolutePath}/$child")

    actual fun isFile(): Boolean {
        return memScoped {
            val isDir = alloc<BooleanVar>()
            val exists = fileManager.fileExistsAtPath(path, isDir.ptr)
            exists && !isDir.value
        }
    }

//    actual fun isDirectory(): Boolean {
//        return kotlinx.cinterop.memScoped {
//            val isDir = alloc<kotlinx.cinterop.BooleanVar>()
//            val exists = fileManager.fileExistsAtPath(path, isDir.ptr)
//            exists && isDir.value
//        }
//    }

    actual fun isDirectory(): Boolean {
        if (path.isBlank()) return false // Ensure path is non-blank and prevent failures.

        return memScoped {
            val isDir = alloc<BooleanVar>() // Allocate memory inside memScoped.
            val exists = fileManager.fileExistsAtPath(path, isDir.ptr) // Check if file exists and isDir is valid.
            exists && isDir.value // Return true only if the file exists and is a directory.
        }
    }

    actual fun canRead(): Boolean {
        return fileManager.isReadableFileAtPath(path)
    }

    actual fun canWrite(): Boolean {
        return fileManager.isWritableFileAtPath(path)
    }
}


//actual class FilePath(private val path: String) {
//    actual val absolutePath: String
//        get() = path
//
//    actual val parent: FilePath?
//        get() = path.substringBeforeLast("/", "").takeIf { it.isNotEmpty() }?.let { FilePath(it) }
//
//    actual val name: String
//        get() = path.substringAfterLast("/")
//
//    actual val nameWithoutExtension: String
//        get() = name.substringBeforeLast(".", name)
//
//    actual val extension: String
//        get() = name.substringAfterLast(".", "")
//
//    actual fun exists(): Boolean = TODO("Implement using platform-specific file check")
//
//    actual fun writeText(text: String) = TODO("Implement using platform-specific file write")
//
//    actual fun readText(): String = TODO("Implement using platform-specific file read")
//
//    actual fun toPath(): FilePath = this
//
//    actual fun absolutePathString(): String = this.absolutePath
//}

