package id.walt.cli.io

expect class File(path: String) {

    //    constructor(parent: String, child: String)
    constructor(parent: File, child: String)

    val absolutePath: String
    val parent: File?
    val name: String
    val nameWithoutExtension: String
    val extension: String

    fun exists(): Boolean
    fun writeText(text: String)
    fun readText(): String
    fun toPath(): Path
    fun absolutePathString(): String
    fun delete(): Boolean

    fun isFile(): Boolean
    fun isDirectory(): Boolean
    fun canRead(): Boolean
    fun canWrite(): Boolean
}

