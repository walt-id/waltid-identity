package id.walt.cli.io

actual fun <R> Path.useLines(block: (Sequence<String>) -> R): R {
    val content = this.toFile().readText()
    val lines = content.lineSequence()
    return block(lines)
}