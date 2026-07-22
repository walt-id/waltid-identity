package id.walt.crypto2.examples

import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    runExampleCommand(
        command = args.singleOrNull() ?: "list",
        commands = jvmExampleCommands,
    )
}
