package id.walt.cli.io

expect class FileSystem {
    fun createFile(path: String): File
    fun getPath(path: String): Path
    fun readResourceAsText(path: String): String
}