package id.walt.cli.io


import java.nio.file.Paths

actual fun <R> Path.useLines(block: (Sequence<String>) -> R): R {
    val javaPath = Paths.get(this.toAbsolutePath())
    java.nio.file.Files.newBufferedReader(javaPath).use { reader ->
        return reader.lineSequence().let(block)
    }
}