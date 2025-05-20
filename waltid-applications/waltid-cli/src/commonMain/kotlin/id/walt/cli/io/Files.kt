@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package id.walt.cli.io

expect class Files {
    companion object {
        fun exists(it: Path): Boolean
        fun isRegularFile(it: Path): Boolean
        fun isDirectory(it: Path): Boolean
        fun isWritable(it: Path): Boolean
        fun isReadable(it: Path): Boolean
        fun isSymbolicLink(it: Path): Boolean
        fun createTempFile(fileName: String, extension: String): File
    }
}