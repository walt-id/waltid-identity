package id.walt.crypto2.examples

suspend fun main() {
    runExampleCommand(
        command = "all",
        commands = wasmExampleCommands,
    )
}
