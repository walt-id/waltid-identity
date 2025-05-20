package id.walt.cli.io

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class Path(path: String) {

    fun toFile(): File
    fun exists(): Boolean
    fun toAbsolutePath(): String
    override fun toString(): String
}