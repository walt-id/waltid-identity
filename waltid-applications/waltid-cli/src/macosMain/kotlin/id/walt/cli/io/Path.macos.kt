package id.walt.cli.io

import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.stringByStandardizingPath

actual class Path actual constructor(val path: String) {

    private val nsPath: NSString = path as NSString

    actual fun toFile(): File {
        return File(path)  // assuming `File` is your own multiplatform abstraction
    }

    actual fun exists(): Boolean {
        return NSFileManager.defaultManager.fileExistsAtPath(path)
    }

    actual fun toAbsolutePath(): String {
        return nsPath.stringByStandardizingPath ?: path
    }
}