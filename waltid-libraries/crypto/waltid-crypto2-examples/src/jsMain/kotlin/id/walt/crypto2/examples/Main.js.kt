package id.walt.crypto2.examples

@Suppress("UnsafeCastFromDynamic")
private fun commandLineArguments(): Array<String> =
    js("typeof process !== 'undefined' ? process.argv.slice(2) : []")

suspend fun main() {
    val args = commandLineArguments()
    runExampleCommand(
        command = args.singleOrNull() ?: "all",
        commands = jsExampleCommands,
    )
}
