package id.walt.cli.io

import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.stringByStandardizingPath

actual class Path actual constructor(val path: String) {

    @OptIn(BetaInteropApi::class)
    private val nsPath: NSString = NSString.create(string = path)

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