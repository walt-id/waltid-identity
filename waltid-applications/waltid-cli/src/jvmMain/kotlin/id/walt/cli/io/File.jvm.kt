package id.walt.cli.io

import java.nio.file.Paths

actual class File actual constructor(val path: String) {

    var nativeFile = java.io.File(path)

    actual constructor(parent: File, child: String) : this(child) {
        val fullFilePath = Paths.get(parent.absolutePath, child)
        nativeFile = fullFilePath.toFile()
    }

    actual val absolutePath: String
        get() = nativeFile.absolutePath

    actual val parent: File?
        get() = File(nativeFile.parent)

    actual val name: String
        get() = nativeFile.getName() //.substringAfterLast("/")

    actual val nameWithoutExtension: String
        get() = nativeFile.name.substringBeforeLast(".")

    actual val extension: String
        get() = nativeFile.name.substringAfterLast(".")

    actual fun exists(): Boolean {
        return nativeFile.exists()
    }

    actual fun writeText(text: String) {
        nativeFile.writeText(text)
    }

    actual fun readText(): String {
        return nativeFile.readText()
    }

    actual fun toPath(): Path {
        return FileSystems.getDefault().getPath(nativeFile.absolutePath)
    }

    actual fun absolutePathString(): String = this.absolutePath

    actual fun isFile(): Boolean {
        return nativeFile.isFile()
    }

    actual fun isDirectory(): Boolean {
        return nativeFile.isDirectory()
    }

    actual fun canRead(): Boolean {
        return nativeFile.canRead()
    }

    actual fun canWrite(): Boolean {
        return nativeFile.canWrite()
    }

}

